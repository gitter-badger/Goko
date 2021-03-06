package org.goko.tinyg.controller;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.goko.core.common.GkUtils;
import org.goko.core.common.applicative.logging.IApplicativeLogService;
import org.goko.core.common.event.EventDispatcher;
import org.goko.core.common.event.EventListener;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.exception.GkFunctionalException;
import org.goko.core.common.exception.GkTechnicalException;
import org.goko.core.common.measure.quantity.Angle;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Quantity;
import org.goko.core.common.measure.quantity.type.BigDecimalQuantity;
import org.goko.core.common.measure.units.Unit;
import org.goko.core.connection.IConnectionService;
import org.goko.core.controller.action.IGkControllerAction;
import org.goko.core.controller.bean.EnumControllerAxis;
import org.goko.core.controller.bean.MachineState;
import org.goko.core.controller.bean.MachineValue;
import org.goko.core.controller.bean.MachineValueDefinition;
import org.goko.core.controller.bean.ProbeResult;
import org.goko.core.controller.event.MachineValueUpdateEvent;
import org.goko.core.gcode.bean.GCodeCommand;
import org.goko.core.gcode.bean.GCodeContext;
import org.goko.core.gcode.bean.IGCodeProvider;
import org.goko.core.gcode.bean.Tuple6b;
import org.goko.core.gcode.bean.commands.EnumCoordinateSystem;
import org.goko.core.gcode.bean.commands.EnumGCodeCommandDistanceMode;
import org.goko.core.gcode.bean.execution.ExecutionQueue;
import org.goko.core.gcode.bean.provider.GCodeExecutionToken;
import org.goko.core.gcode.service.IGCodeExecutionMonitorService;
import org.goko.core.gcode.service.IGCodeService;
import org.goko.core.log.GkLog;
import org.goko.tinyg.controller.configuration.TinyGConfiguration;
import org.goko.tinyg.controller.configuration.TinyGConfigurationValue;
import org.goko.tinyg.controller.configuration.TinyGGroupSettings;
import org.goko.tinyg.controller.configuration.TinyGSetting;
import org.goko.tinyg.controller.prefs.TinyGPreferences;
import org.goko.tinyg.controller.probe.ProbeCallable;
import org.goko.tinyg.json.TinyGJsonUtils;
import org.goko.tinyg.service.ITinyGControllerFirmwareService;

import com.eclipsesource.json.JsonObject;

/**
 * Implementation of the TinyG controller
 *
 * @author PsyKo
 *
 */
public class TinyGControllerService extends EventDispatcher implements ITinyGControllerFirmwareService, ITinygControllerService{
	static final GkLog LOG = GkLog.getLogger(TinyGControllerService.class);
	/**  Service ID */
	public static final String SERVICE_ID = "TinyG Controller";
	private static final String JOG_SIMULATION_DISTANCE = "10000.0";
	private static final double JOG_SIMULATION_DISTANCE_DOUBLE = 10000.0;

	/** Stored configuration */
	private TinyGConfiguration configuration;
	/** Connection service */
	private IConnectionService connectionService;
	/** GCode service */
	private IGCodeService gcodeService;
	/** applicative log service */
	private IApplicativeLogService applicativeLogService;
	/** The sending thread	 */
	private GCodeSendingRunnable currentSendingRunnable;
	/** The current execution queue */
	private ExecutionQueue<TinyGExecutionToken> executionQueue;
	/** The monitor service */
	private IGCodeExecutionMonitorService monitorService;
	/** Action factory */
	private TinyGActionFactory actionFactory;
	/** Storage object for machine values (speed, position, etc...) */
	private TinyGState tinygState;
	/** Waiting probe result */
	private ProbeCallable futureProbeResult;
	/** Communicator */
	private TinyGCommunicator communicator;

	public TinyGControllerService() {
		communicator = new TinyGCommunicator(this);		
	}
	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#getServiceId()
	 */
	@Override
	public String getServiceId() throws GkException {
		return SERVICE_ID;
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#start()
	 */
	@Override
	public void start() throws GkException {
		configuration 			= new TinyGConfiguration();
		actionFactory 			= new TinyGActionFactory(this);
		tinygState = new TinyGState();
		tinygState.addListener(this);

		TinyGPreferences.getInstance();
		
		// Initiate execution queue
		executionQueue 				= new ExecutionQueue<TinyGExecutionToken>();
		ExecutorService executor 	= Executors.newSingleThreadExecutor();
		currentSendingRunnable 		= new GCodeSendingRunnable(executionQueue, this);
		executor.execute(currentSendingRunnable);
		
		
		LOG.info("Successfully started "+getServiceId());
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#stop()
	 */
	@Override
	public void stop() throws GkException {
		if(currentSendingRunnable != null){
			currentSendingRunnable.stop();
		}
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getPosition()
	 */
	@Override
	public Tuple6b getPosition() throws GkException {
		return tinygState.getWorkPosition();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#executeGCode(org.goko.core.gcode.bean.IGCodeProvider)
	 */
	@Override
	public GCodeExecutionToken executeGCode(IGCodeProvider gcodeProvider) throws GkException{
		checkExecutionControl();
		if(!getConnectionService().isConnected()){
			throw new GkFunctionalException("TNG-002");
		}
		updateQueueReport();
		TinyGExecutionToken token = new TinyGExecutionToken(gcodeProvider);
		token.setMonitorService(getMonitorService());
		executionQueue.add(token);

		return token;
	}

	private void checkExecutionControl() throws GkException{
		BigDecimal qrVerbosity = configuration.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.QUEUE_REPORT_VERBOSITY, BigDecimal.class);
		BigDecimal flowControl = configuration.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.ENABLE_FLOW_CONTROL, BigDecimal.class);

		// We always need to use flow control
		if(ObjectUtils.equals(flowControl, TinyGConfigurationValue.FLOW_CONTROL_OFF)){				
			throw new GkFunctionalException("TNG-001");				
		}
		
		if(isPlannerBufferSpaceCheck()){
			if(ObjectUtils.equals(qrVerbosity, TinyGConfigurationValue.QUEUE_REPORT_OFF)){
				throw new GkFunctionalException("TNG-002");
			}
		}
	}
	public void send(GCodeCommand gCodeCommand) throws GkException{
		communicator.send(gCodeCommand);
	}

	public void sendTogether(List<GCodeCommand> commands) throws GkException{
		for (GCodeCommand gCodeCommand : commands) {
			communicator.send(gCodeCommand);
		}
	}

	@Override
	public boolean isReadyForFileStreaming() throws GkException {
		return MachineState.READY.equals(getState())
			|| MachineState.PROGRAM_END.equals(getState())
			|| MachineState.PROGRAM_STOP.equals(getState());
	}
	/**
	 * Refresh the TinyG configuration by sending all the Groups as empty groups
	 * Update is done by event handling
	 *
	 * @throws GkException GkException
	 */
	@Override
	public void refreshConfiguration() throws GkException{
		for(TinyGGroupSettings group : configuration.getGroups()){
			JsonObject groupEmpty = new JsonObject();
			groupEmpty.add(group.getGroupIdentifier(), StringUtils.EMPTY);
			communicator.send(GkUtils.toBytesList(groupEmpty.toString()));
		}
	}
	public void refreshStatus() throws GkException{
		JsonObject statusQuery = new JsonObject();
		statusQuery.add("sr", StringUtils.EMPTY);
		communicator.send(GkUtils.toBytesList(statusQuery.toString()));
		updateQueueReport();
	}

	protected void updateQueueReport()throws GkException{
		JsonObject queueQuery = new JsonObject();
		queueQuery.add("qr", StringUtils.EMPTY);
		communicator.send(GkUtils.toBytesList(queueQuery.toString()));
	}

	/**
	 * Update the current GCodeContext with the given one
	 * @param updatedGCodeContext the updated GCodeContext
	 */
	protected void updateCurrentGCodeContext(GCodeContext updatedGCodeContext){
		GCodeContext current = tinygState.getGCodeContext();
		if(updatedGCodeContext.getPosition() != null){
			current.setPosition(updatedGCodeContext.getPosition());
		}
		if(updatedGCodeContext.getCoordinateSystem() != null){
			current.setCoordinateSystem(updatedGCodeContext.getCoordinateSystem());
		}
		if(updatedGCodeContext.getMotionMode() != null){
			current.setMotionMode(updatedGCodeContext.getMotionMode());
		}
		if(updatedGCodeContext.getMotionType() != null){
			current.setMotionType(updatedGCodeContext.getMotionType());
		}
		if(updatedGCodeContext.getFeedrate() != null){
			current.setFeedrate(updatedGCodeContext.getFeedrate());
		}
		if(updatedGCodeContext.getUnit() != null){
			current.setUnit(updatedGCodeContext.getUnit());
		}
		if(updatedGCodeContext.getDistanceMode() != null){
			current.setDistanceMode(updatedGCodeContext.getDistanceMode());
		}
		if(updatedGCodeContext.getPlane() != null){
			current.setPlane(updatedGCodeContext.getPlane());
		}
		if(updatedGCodeContext.getToolNumber() != null){
			current.setToolNumber(updatedGCodeContext.getToolNumber());
		}
		tinygState.setGCodeContext(current);
	}

	/**
	 * Return a copy of the current stored configuration
	 * @return a copy of {@link TinyGConfiguration}
	 * @throws GkException GkException
	 */
	@Override
	public TinyGConfiguration getConfiguration() throws GkException{
		return TinyGControllerUtility.getConfigurationCopy(configuration);
	}

	/**
	 * Returned the available {@link IConnectionService}
	 * @return the connectionService
	 */
	public IConnectionService getConnectionService() {
		return connectionService;
	}

	/**
	 * @param connectionService the connectionService to set
	 * @throws GkException GkException
	 */
	public void setConnectionService(IConnectionService connectionService) throws GkException {
		this.connectionService = connectionService;
		communicator.setConnectionService(connectionService);
	}

	/**
	 * Handling GCode response from TinyG
	 * @param jsonValue
	 * @throws GkTechnicalException
	 */
	protected void handleGCodeResponse(String receivedCommand) throws GkException {
		if(executionQueue.getCurrentToken() != null){
			GCodeCommand 	parsedCommand 	= getGcodeService().parseCommand(receivedCommand, getCurrentGCodeContext());
			executionQueue.getCurrentToken().markAsConfirmed(parsedCommand);
			this.currentSendingRunnable.confirmCommand();
		}
	}

	@EventListener(MachineValueUpdateEvent.class)
	public void onMachineValueUpdate(MachineValueUpdateEvent evt){
		notifyListeners(evt);
	}

	/** (inheritDoc)
	 * @see org.goko.tinyg.service.ITinyGControllerFirmwareService#setConfiguration(org.goko.tinyg.controller.configuration.TinyGConfiguration)
	 */
	@Override
	public void setConfiguration(TinyGConfiguration cfg) throws GkException{
		this.configuration = TinyGControllerUtility.getConfigurationCopy(cfg);
	}

	/** (inheritDoc)
	 * @see org.goko.tinyg.service.ITinyGControllerFirmwareService#updateConfiguration(org.goko.tinyg.controller.configuration.TinyGConfiguration)
	 */
	@Override
	public void updateConfiguration(TinyGConfiguration cfg) throws GkException {
		// Let's only change the new values
		// The new value will be applied directly to TinyG. The changes will be reported in the data model when TinyG sends them back for confirmation.
		TinyGConfiguration diffConfig = TinyGControllerUtility.getDifferentialConfiguration(getConfiguration(), cfg);
		// TODO : perform sending in communicator
		for(TinyGGroupSettings group: diffConfig.getGroups()){
			if(StringUtils.equals(group.getGroupIdentifier(), TinyGConfiguration.SYSTEM_SETTINGS)){
				for(TinyGSetting<?> setting : group.getSettings()){
					JsonObject jsonSetting = TinyGJsonUtils.toJson(setting);
					if(jsonSetting != null){
						getConnectionService().send( GkUtils.toBytesList(jsonSetting.toString() + "\r\n") );
					}
				}
			}else{
				JsonObject jsonGroup = TinyGJsonUtils.toCompleteJson(group);
				if(jsonGroup != null){
					getConnectionService().send( GkUtils.toBytesList(jsonGroup.toString() + "\r\n") );
				}
			}
		}
	}


	/**
	 * @return the gcodeService
	 */
	public IGCodeService getGcodeService() {
		return gcodeService;
	}

	/**
	 * @param gCodeService the gcodeService to set
	 */
	public void setGCodeService(IGCodeService gCodeService) {
		this.gcodeService = gCodeService;
		this.communicator.setGcodeService(gCodeService);
	}

	@Override
	public MachineState getState() throws GkException {
		return tinygState.getState();
	}

	public void setState(MachineState state) throws GkException {
		tinygState.setState(state);
	}

	public boolean isSpindleOn() throws GkException{
		return tinygState.isSpindleOn();
	}

	public boolean isSpindleOff() throws GkException{
		return tinygState.isSpindleOff();
	}

	/**
	 * Initiate TinyG homing sequence
	 * @throws GkException GkException
	 */
	public void startHomingSequence() throws GkException{
		String 		homingCommand 		= "G28.2";
		if(TinyGPreferences.getInstance().isHomingEnabledAxisX()){
			homingCommand += " X0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisY()){
			homingCommand += " Y0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisZ()){
			homingCommand += " Z0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisA()){
			homingCommand += " A0";
		}		
		communicator.send(GkUtils.toBytesList(homingCommand));
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getControllerAction(java.lang.String)
	 */
	@Override
	public IGkControllerAction getControllerAction(String actionId) throws GkException {
		IGkControllerAction action = actionFactory.findAction(actionId);
		if(action == null){
			throw new GkFunctionalException("TNG-004", actionId, getServiceId());
		}
		return action;
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#isControllerAction(java.lang.String)
	 */
	@Override
	public boolean isControllerAction(String actionId) throws GkException {
		return actionFactory.findAction(actionId) != null;
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getMachineValue(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T> MachineValue<T> getMachineValue(String name, Class<T> clazz) throws GkException {
		return tinygState.getValue(name, clazz);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getMachineValueType(java.lang.String)
	 */
	@Override
	public Class<?> getMachineValueType(String name) throws GkException {
		return tinygState.getControllerValueType(name);
	}

	@Override
	public List<MachineValueDefinition> getMachineValueDefinition() throws GkException {
		return tinygState.getMachineValueDefinition();
	}
	@Override
	public MachineValueDefinition getMachineValueDefinition(String id) throws GkException {
		return tinygState.getMachineValueDefinition(id);
	}
	@Override
	public MachineValueDefinition findMachineValueDefinition(String id) throws GkException {
		return tinygState.findMachineValueDefinition(id);
	}


	/* ************************************************
	 *  CONTROLLER ACTIONS
	 * ************************************************/


	public void pauseMotion() throws GkException{
		communicator.send(GkUtils.toBytesList(TinyG.FEED_HOLD));
		if(executionQueue != null){
			executionQueue.setPaused(true);
		}
	}

	public void resumeMotion() throws GkException{
		communicator.send(GkUtils.toBytesList(TinyG.CYCLE_START));
	}

	public void stopMotion() throws GkException{
		getConnectionService().clearOutputBuffer();
		communicator.sendImmediately(GkUtils.toBytesList(TinyG.FEED_HOLD, TinyG.QUEUE_FLUSH));


		if(executionQueue != null){
			executionQueue.clear();
		}
		if(currentSendingRunnable != null){
			currentSendingRunnable.stop();
		}
		// Force a queue report update
		//	updateQueueReport();
		//	this.resetAvailableBuffer();
	}

	public void resetZero(List<String> axes) throws GkException{
		List<Byte> lstBytes = GkUtils.toBytesList("G28.3");
		if(CollectionUtils.isNotEmpty(axes)){
			for (String axe : axes) {
				lstBytes.addAll(GkUtils.toBytesList(axe+"0"));
			}
		}else{
			lstBytes.addAll( GkUtils.toBytesList("X0Y0Z0"));
		}
		communicator.send(lstBytes);
	}

	public void startJog(EnumTinyGAxis axis, BigDecimal feed) throws GkException{
		String command = StringUtils.EMPTY;
		if(getCurrentGCodeContext().getDistanceMode() == EnumGCodeCommandDistanceMode.ABSOLUTE){
			command = startG90Jog(axis, feed);
		}else{
			command = startG91Jog(axis, feed);
		}
		if(feed != null){
			command += "F"+feed;
		}
		communicator.send(GkUtils.toBytesList(command));
	}

	public String startG91Jog(EnumTinyGAxis axis, BigDecimal feed) throws GkException{
		String command = "G1"+axis.getAxisCode();
		double delta = JOG_SIMULATION_DISTANCE_DOUBLE;
		double target = 0;
		switch (axis) {
		case X_NEGATIVE: target = getX().doubleValue() - delta;
			break;
		case X_POSITIVE: target = getX().doubleValue() + delta;
			break;
		case Y_NEGATIVE: target = getY().doubleValue() - delta;
			break;
		case Y_POSITIVE: target = getY().doubleValue() + delta;
			break;
		case Z_NEGATIVE: target = getZ().doubleValue() - delta;
			break;
		case Z_POSITIVE: target = getZ().doubleValue() + delta;
			break;
		case A_NEGATIVE: target = getA().doubleValue() - delta;
			break;
		case A_POSITIVE: target = getA().doubleValue() + delta;
			break;
		default:
			break;
		}
		command += String.valueOf(target);
		return command;
	}
	public String startG90Jog(EnumTinyGAxis axis, BigDecimal feed){
		String command = "G1"+axis.getAxisCode();
		if(axis.isNegative()){
			command+="-";
		}		
		command += JOG_SIMULATION_DISTANCE;
		return command;
	}
	public void turnSpindleOn() throws GkException{
		communicator.send(GkUtils.toBytesList("M3"));
	}
	public void turnSpindleOff() throws GkException{
		communicator.send(GkUtils.toBytesList("M5"));
	}

	/**
	 * @return the availableBuffer
	 * @throws GkException
	 */
	public int getAvailableBuffer() throws GkException {
		return tinygState.getAvailableBuffer();
	}
	/**
	 * @param availableBuffer the availableBuffer to set
	 * @throws GkException exception
	 */
	public void setAvailableBuffer(int availableBuffer) throws GkException {
		tinygState.updateValue(TinyG.TINYG_BUFFER_COUNT, availableBuffer);
		if(currentSendingRunnable != null){
			currentSendingRunnable.notifyBufferSpace();
		}
	}

	public void resetAvailableBuffer() throws GkException {
		setAvailableBuffer(28);
	}

	@Override
	public void cancelFileSending() throws GkException {
		stopMotion();
	}


	@Override
	public String getMinimalSupportedFirmwareVersion() throws GkException {
		return "435.10";
	}

	@Override
	public String getMaximalSupportedFirmwareVersion() throws GkException {
		return "435.10";
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IProbingService#probe(org.goko.core.controller.bean.EnumControllerAxis, double, double)
	 */
	@Override
	public Future<ProbeResult> probe(EnumControllerAxis axis, double feedrate, double maximumPosition) throws GkException {
		futureProbeResult = new ProbeCallable();
		String strCommand = "G38.2 "+axis.getAxisCode()+String.valueOf(maximumPosition)+" F"+feedrate;
		IGCodeProvider command = gcodeService.parse(strCommand, getCurrentGCodeContext());
		executeGCode(command);
		return Executors.newSingleThreadExecutor().submit(futureProbeResult);
	}

	protected void handleProbeResult(boolean probed, Tuple6b position){
		if(this.futureProbeResult != null){
			ProbeResult probeResult = new ProbeResult();
			probeResult.setProbed(probed);
			probeResult.setProbedPosition(position);
			this.futureProbeResult.setProbeResult(probeResult);
		}
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#moveToAbsolutePosition(org.goko.core.gcode.bean.Tuple6b)
	 */
	@Override
	public void moveToAbsolutePosition(Tuple6b position) throws GkException {
		String cmd = "G1F800";
		if(position.getX() != null){			
			cmd += "X"+getPositionAsString(position.getX());
		}
		if(position.getY() != null){
			cmd += "Y"+getPositionAsString(position.getY());
		}
		if(position.getZ() != null){
			cmd += "Z"+getPositionAsString(position.getZ());
		}
		IGCodeProvider command = gcodeService.parse(cmd, getCurrentGCodeContext());
		executeGCode(command);
	}

	protected String getPositionAsString(BigDecimalQuantity<Length> q){
		return String.valueOf(q.to(getCurrentUnit()).getValue());
	}
	/**
	 * @param logListenerService the logListenerService to set
	 */
	public void setApplicativeLogService(IApplicativeLogService logListenerService) {
		this.applicativeLogService = logListenerService;
		this.communicator.setApplicativeLogService(logListenerService);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getCurrentGCodeContext()
	 */
	@Override
	public GCodeContext getCurrentGCodeContext() throws GkException {
		return tinygState.getGCodeContext();
	}

	public void setVelocity(BigDecimal velocity) throws GkException {
		tinygState.setVelocity(velocity);
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getX()
	 */
	@Override
	public Quantity<Length> getX() throws GkException {
		return tinygState.getX();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getY()
	 */
	@Override
	public Quantity<Length> getY() throws GkException {
		return tinygState.getY();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getZ()
	 */
	@Override
	public Quantity<Length> getZ() throws GkException {
		return tinygState.getZ();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IFourAxisControllerAdapter#getA()
	 */
	@Override
	public Quantity<Angle> getA() throws GkException {
		return tinygState.getA();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCoordinateSystemOffset(org.goko.core.gcode.bean.commands.EnumCoordinateSystem)
	 */
	@Override
	public Tuple6b getCoordinateSystemOffset(EnumCoordinateSystem cs) throws GkException {
		return tinygState.getCoordinateSystemOffset(cs);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCurrentCoordinateSystem()
	 */
	@Override
	public EnumCoordinateSystem getCurrentCoordinateSystem() throws GkException {
		return tinygState.getGCodeContext().getCoordinateSystem();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCoordinateSystem()
	 */
	@Override
	public List<EnumCoordinateSystem> getCoordinateSystem() throws GkException {
		return Arrays.asList(EnumCoordinateSystem.values());
	}
	public void setCoordinateSystemOffset(EnumCoordinateSystem cs, Tuple6b offset) throws GkException {
		tinygState.setCoordinateSystemOffset(cs, offset);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IContinuousJogService#startJog(org.goko.core.controller.bean.EnumControllerAxis, double)
	 */
	@Override
	public void startJog(EnumControllerAxis axis, BigDecimal feedrate) throws GkException {
		startJog( EnumTinyGAxis.getEnum(axis.getCode()), feedrate);
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IContinuousJogService#stopJog()
	 */
	@Override
	public void stopJog() throws GkException {
		stopMotion();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#setCurrentCoordinateSystem(org.goko.core.gcode.bean.commands.EnumCoordinateSystem)
	 */
	@Override
	public void setCurrentCoordinateSystem(EnumCoordinateSystem cs) throws GkException {
		communicator.send( GkUtils.toBytesList( String.valueOf(cs)) );
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#resetCurrentCoordinateSystem()
	 */
	@Override
	public void resetCurrentCoordinateSystem() throws GkException {
		EnumCoordinateSystem current = getCurrentCoordinateSystem();
		Tuple6b offsets = getCoordinateSystemOffset(current);
		Tuple6b mPos = new Tuple6b(tinygState.getWorkPosition());
		mPos = mPos.add(offsets);
		String cmd = "{\""+String.valueOf(current) +"\":{";
		
		cmd += "\"x\":"+ getPositionAsString(mPos.getX()) +", ";		
		cmd += "\"y\":"+ getPositionAsString(mPos.getY())+", ";
		cmd += "\"z\":"+ getPositionAsString(mPos.getZ())+"}} ";
		communicator.send( GkUtils.toBytesList( cmd ) );
		communicator.updateCoordinateSystem(current);		
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPlannerBufferSpaceCheck() {
		return TinyGPreferences.getInstance().isPlannerBufferSpaceCheck();
	}
	/**
	 * @param plannerBufferSpaceCheck the plannerBufferSpaceCheck to set
	 * @throws GkTechnicalException
	 */
	@Override
	public void setPlannerBufferSpaceCheck(boolean value) throws GkTechnicalException {
		TinyGPreferences.getInstance().setPlannerBufferSpaceCheck(value);
	}
	/**
	 * @return the monitorService
	 */
	public IGCodeExecutionMonitorService getMonitorService() {
		return monitorService;
	}
	/**
	 * @param monitorService the monitorService to set
	 */
	public void setMonitorService(IGCodeExecutionMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	public Unit<Length> getCurrentUnit(){
		return tinygState.getCurrentUnit();
	}
}

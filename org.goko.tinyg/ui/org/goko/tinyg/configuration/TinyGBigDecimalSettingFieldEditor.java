package org.goko.tinyg.configuration;

import java.math.BigDecimal;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.goko.common.preferences.fieldeditor.BigDecimalFieldEditor;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.utils.BigDecimalUtils;
import org.goko.tinyg.controller.configuration.TinyGConfiguration;

public class TinyGBigDecimalSettingFieldEditor extends BigDecimalFieldEditor implements ITinyGFieldEditor<Text>{
	String groupIdentifier;	
	TinyGConfiguration cfg;
	
	public TinyGBigDecimalSettingFieldEditor(Composite parent, int style) {
		super(parent, style);		
	}
	
	
	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.FieldEditor#setDefaultValue()
	 */
	@Override
	protected void setDefaultValue() throws GkException {
		getControl().setText( "0" );
		refreshValidState();
	}

	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.FieldEditor#loadValue()
	 */
	@Override
	protected void loadValue() throws GkException {
		BigDecimal value = cfg.getSetting(groupIdentifier, preferenceName , BigDecimal.class);		
		getControl().setText( BigDecimalUtils.toString(value));
		refreshValidState();		
	}

	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.FieldEditor#storeValue()
	 */
	@Override
	protected void storeValue() throws GkException {		
		BigDecimal decimalValue = BigDecimalUtils.parse(getControl().getText());
		cfg.setSetting(groupIdentifier, getPreferenceName(), decimalValue);		
	}


	/**
	 * @return the groupIdentifier
	 */
	public String getGroupIdentifier() {
		return groupIdentifier;
	}


	/**
	 * @param groupIdentifier the groupIdentifier to set
	 */
	public void setGroupIdentifier(String groupIdentifier) {
		this.groupIdentifier = groupIdentifier;
	}


	@Override
	public void setConfiguration(TinyGConfiguration cfg) {
		this.cfg = cfg;
	}
	
}


package org.goko.gcode.rs274ngcv3;

import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.goko.common.GkUiUtils;
import org.goko.common.preferences.GkFieldEditorPreferencesPage;
import org.goko.common.preferences.IPreferenceStoreProvider;
import org.goko.common.preferences.fieldeditor.BooleanFieldEditor;
import org.goko.common.preferences.fieldeditor.IntegerFieldEditor;
import org.goko.core.common.exception.GkException;
import org.goko.gcode.rs274ngcv3.config.RS274Preference;

public class RS274GCodePreferences extends GkFieldEditorPreferencesPage{
	private BooleanFieldEditor truncateEnabledEditor;
	private IntegerFieldEditor decimalCountEditor;

	public RS274GCodePreferences() {
		setTitle("GCode");
		setPreferenceStore(RS274Preference.getInstance());
	}

	@Override
	protected void createPreferencePage(Composite parent) {
		Composite content = new Composite(parent, SWT.NONE);
		content.setLayout(new GridLayout(1, false));
		
		truncateEnabledEditor = new BooleanFieldEditor(content, SWT.NONE);
		truncateEnabledEditor.setPreferenceName(RS274Preference.KEY_TRUNCATE_ENABLED);
		truncateEnabledEditor.setLabel("Truncate decimal");
		
		decimalCountEditor = new IntegerFieldEditor(parent, SWT.NONE);
		decimalCountEditor.setEmptyStringAllowed(false);
		decimalCountEditor.setLabel("Decimal count :");
		decimalCountEditor.setWidthInChars(5);
		decimalCountEditor.setPreferenceName(RS274Preference.KEY_DECIMAL_COUNT);
		
		
		addField(truncateEnabledEditor);	
		addField(decimalCountEditor);
		
	}
	
	protected void validate() {
		super.validate();
	};
	/** (inheritDoc)
	 * @see org.goko.common.preferences.GkFieldEditorPreferencesPage#postInitialize()
	 */
	@Override
	protected void postInitialize() throws GkException {		
		GkUiUtils.setEnabled(decimalCountEditor, truncateEnabledEditor.isSelected());
		decimalCountEditor.setEmptyStringAllowed(truncateEnabledEditor.isSelected());
	}
	/** (inheritDoc)
	 * @see org.goko.common.preferences.GkFieldEditorPreferencesPage#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	@Override
	public void propertyChange(PropertyChangeEvent event) {		
		decimalCountEditor.setEmptyStringAllowed(!truncateEnabledEditor.isSelected());
		GkUiUtils.setEnabled(decimalCountEditor, truncateEnabledEditor.isSelected());		
		super.propertyChange(event);
	}

}

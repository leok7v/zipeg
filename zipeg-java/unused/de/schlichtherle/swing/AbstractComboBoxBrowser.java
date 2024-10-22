/*
 * AbstractComboBoxBrowser.java
 *
 * Created on 31. Juli 2006, 10:32
 */
/*
 * Copyright 2006 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schlichtherle.swing;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;

import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * An observer for a {@link JComboBox} which provides auto completion
 * for the editable text in the drop down list in order to provide quick
 * browsing capabilities for the user.
 * Subclasses need to implement the {@link #update} method in order to update
 * the combo box model with the actual auto completion data.
 * <p>
 * This class is designed to be minimal intrusive: It works with any subclass
 * of <code>JComboBox</code> and doesn't require a special
 * {@link ComboBoxModel}, although its specific behaviour will only show
 * if the <code>JComboBox</code> is <code>editable</code> and uses an
 * instance of a {@link MutableComboBoxModel} (which, apart from the
 * <code>editable</code> property being set to <code>true</code>, is the
 * default for a plain <code>JComboBox</code>).
 *
 * @author Christian Schlichtherle
 * @since TrueZIP 6.2
 * @version @version@
 */
public abstract class AbstractComboBoxBrowser implements Serializable {

    private final Listener listener = new Listener();

    private JComboBox comboBox;
    private String lastInitials;

    /**
     * Used to inhibit mutual recursive event firing.
     */
    private transient boolean recursion;

    /**
     * Creates a new combo box auto completion browser.
     * {@link #setComboBox} must be called in order to use this object.
     */
    public AbstractComboBoxBrowser() {
    }

    /**
     * Creates a new combo box auto completion browser.
     *
     * @param comboBox The combo box to enable browsing for auto completions.
     *        May be <code>null</code>.
     */
    public AbstractComboBoxBrowser(final JComboBox comboBox) {
        changeComboBox(null, comboBox);
    }

    /**
     * Returns the combo box which this object is auto completing.
     * The default is <code>null</code>.
     */
    public JComboBox getComboBox() {
        return comboBox;
    }

    /**
     * Sets the combo box which this object is auto completing.
     *
     * @param comboBox The combo box to enable browsing for auto completions.
     *        May be <code>null</code>.
     */
    public void setComboBox(final JComboBox comboBox) {
        changeComboBox(getComboBox(), comboBox);
    }

    private void changeComboBox(
            final JComboBox oldCB,
            final JComboBox newCB) {
        if (newCB == oldCB)
            return;

        ComboBoxEditor oldCBE = null;
        if (oldCB != null) {
            oldCBE = oldCB.getEditor();
            oldCB.setEditor(((ComboBoxEditorProxy) oldCBE).getEditor());
            oldCB.removePropertyChangeListener("editor", listener);
        }

        this.comboBox = newCB;
        
        ComboBoxEditor newCBE = null;
        if (newCB != null) {
            newCB.updateUI(); // ensure comboBoxEditor is initialized
            newCBE = new ComboBoxEditorProxy(newCB.getEditor());
            newCB.setEditor(newCBE);
            newCB.addPropertyChangeListener("editor", listener);
        }

        changeEditor(oldCBE, newCBE);
    }

    private void changeEditor(
            final ComboBoxEditor oldCBE,
            final ComboBoxEditor newCBE) {
        if (newCBE == oldCBE)
            return;

        JTextComponent oldText = null;
        if (oldCBE != null) {
            final Component component = oldCBE.getEditorComponent();
            if (component instanceof JTextComponent)
                oldText = (JTextComponent) component;
        }

        JTextComponent newText = null;
        if (newCBE != null) {
            final Component component = newCBE.getEditorComponent();
            if (component instanceof JTextComponent)
                newText = (JTextComponent) component;
        }

        changeText(oldText, newText);
    }

    private void changeText(
            final JTextComponent oldTC,
            final JTextComponent newTC) {
        if (newTC == oldTC)
            return;

        Document oldDocument = null;
        if (oldTC != null) {
            oldTC.removePropertyChangeListener("document", listener);
            oldDocument = oldTC.getDocument();
        }

        Document newDocument = null;
        if (newTC != null) {
            newTC.addPropertyChangeListener("document", listener);
            newDocument = newTC.getDocument();
        }

        changeDocument(oldDocument, newDocument);
    }

    private void changeDocument(
            final Document oldDoc,
            final Document newDoc) {
        if (newDoc == oldDoc)
            return;

        if (oldDoc != null)
            oldDoc.removeDocumentListener(listener);

        if (newDoc != null)
            newDoc.addDocumentListener(listener);
    }

    private void documentUpdated() {
        if (lock())
            return;
        try {
            final JComboBox cb = getComboBox();
            final ComboBoxEditorProxy cbep = (ComboBoxEditorProxy) cb.getEditor();
            final JTextComponent tc = (JTextComponent) cbep.getEditorComponent();
            if (!tc.hasFocus())
                return;

            final String text = tc.getText();
            cb.setPopupVisible(false);
            if (updateConditionally(text)) {
                final ComboBoxModel cbm = cb.getModel();
                // Reset the selection if required - may have been changed by
                // the cbm when updating it.
                // This is the case with e.g. DefaultComboBoxModel.
                if (!text.equals(cbm.getSelectedItem()))
                    cbm.setSelectedItem(text);
                cb.setPopupVisible(true);
            }
        } finally {
            unlock();
        }
    }

    private void updateEditor(final ComboBoxEditor cbe, final Object item) {
        if (lock())
            return;
        try {
            cbe.setItem(item);
            if (!(item instanceof String))
                return;

            final JTextComponent tc = (JTextComponent) cbe.getEditorComponent();
            if (!tc.hasFocus())
                return;

            // Compensate for an issue with some look and feels
            // which select the entire tc if an item is changed.
            // This is inconvenient for auto completion because the
            // next typed character would replace the entire tc...
            final Caret caret = tc.getCaret();
            caret.setDot(((String) item).length());
        } finally {
            unlock();
        }
    }

    private final boolean updateConditionally(final String initials) {
        if (initials.equals(lastInitials))
            return false;
        else {
            lastInitials = initials;
            return update(initials);
        }
    }

    /**
     * Subclasses are expected to update the auto completion elements in the
     * model of this combo box based on the specified <code>initials</code>.
     * They should not do any other work within this method.
     * In particular, they should not update the visual appearance of this
     * component.
     * <p>
     * {@link #getComboBox} is guaranteed to return non-<code>null</code> if
     * this method is called from this abstract base class.
     *
     * @param initials The text to auto complete.
     * @return Whether or not the combo box should pop up to show the updated
     *         contents of its model.
     */
    protected abstract boolean update(String initials);

    /**
     * Locks out mutual recursive event notification.
     * <b>Warning:</b> This method works in a synchronized or single threaded
     * environment only!
     * 
     * @return Whether or not updating the combo box model was already locked.
     */
    private final boolean lock() {
        if (recursion)
            return true;
        recursion = true;
        return false;
    }

    /**
     * Unlocks mutual recursive event notification.
     * <b>Warning:</b> This method works in a synchronized or single threaded
     * environment only!
     */
    private final void unlock() {
        recursion = false;
    }

    private final class Listener
            implements DocumentListener, PropertyChangeListener {
        public void insertUpdate(DocumentEvent e) {
            documentUpdated();
        }

        public void removeUpdate(DocumentEvent e) {
            documentUpdated();
        }

        public void changedUpdate(DocumentEvent e) {
            documentUpdated();
        }

        public void propertyChange(final PropertyChangeEvent e) {
            final String property = e.getPropertyName();
            if ("editor".equals(property))
                changeEditor(   (ComboBoxEditor) e.getOldValue(),
                                (ComboBoxEditor) e.getNewValue());
            else if ("document".equals(property))
                changeDocument( (Document) e.getOldValue(),
                                (Document) e.getNewValue());
            else
                throw new AssertionError(
                        "Received change event for unknown property: "
                        + property);
        }
    }

    /**
     * This proxy controls access to the real <code>ComboBoxEditor</code>
     * installed by the client application or the pluggable look and feel.
     * It is used to lock out mutual recursion caused by modifications to
     * the list model in the <code>JComboBox</code>.
     * <p>
     * Note that there is a slight chance that the introduction of this proxy
     * breaks the look and feel if it does <code>instanceof</code> tests for
     * a particular class, but I'm not aware of any look and feel which is
     * actually affected.
     * In order to reduce this risk, this class is extended from
     * {@link BasicComboBoxEditor}, although it overrides all methods which
     * are defined in the {@link ComboBoxEditor} interface.
     */
    private final class ComboBoxEditorProxy
            extends BasicComboBoxEditor
            implements ComboBoxEditor {
        private final ComboBoxEditor comboBoxEditor;

        public ComboBoxEditorProxy(ComboBoxEditor comboBoxEditor) {
            this.comboBoxEditor = comboBoxEditor;
        }

        public ComboBoxEditor getEditor() {
            return comboBoxEditor;
        }
        
        public Component getEditorComponent() {
            return comboBoxEditor.getEditorComponent();
        }

        public void setItem(final Object item) {
            updateEditor(comboBoxEditor, item);
        }

        public Object getItem() {
            return comboBoxEditor.getItem();
        }

        public void selectAll() {
            comboBoxEditor.selectAll();
        }

        public void addActionListener(ActionListener actionListener) {
            comboBoxEditor.addActionListener(actionListener);
        }

        public void removeActionListener(ActionListener actionListener) {
            comboBoxEditor.removeActionListener(actionListener);
        }
    }
}

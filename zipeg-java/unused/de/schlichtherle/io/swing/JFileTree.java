/*
 * JFileTree.java
 *
 * Created on 13. Februar 2006, 15:42
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

package de.schlichtherle.io.swing;

import de.schlichtherle.io.swing.tree.FileTreeModel;
import de.schlichtherle.io.swing.tree.FileTreeCellRenderer;

import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.swing.CellEditor;
import javax.swing.JTree;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * A customized {@link JTree} which browses {@link java.io.File} instances.
 * This allows for convenient traversing of directories as well as any ZIP
 * compatible file recognized by TrueZIP.
 * There are a couple of file creation/modification/removal methods added
 * which notify the tree of any changes in the file system and update the
 * current path expansions and selection.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 5.1
 */
public class JFileTree extends JTree {
    
    /** The name of the property <code>displayingSuffixes</code>. */
    private static final String PROPERTY_DISPLAYING_SUFFIXES = "displayingSuffixes";

    /** The name of the property <code>editingSuffixes</code>. */
    private static final String PROPERTY_EDITING_SUFFIXES = "editingSuffixes";

    /** The name of the property <code>defaultSuffix</code>. */
    private static final String PROPERTY_DEFAULT_SUFFIX = "defaultSuffix";

    private final TreeExpansionListener tel = new TreeExpansionListener() {
        public void treeCollapsed(TreeExpansionEvent evt) {
            ((FileTreeModel) getModel()).forget(
                    (java.io.File) evt.getPath().getLastPathComponent());
        }

        public void treeExpanded(TreeExpansionEvent evt) {
        }
    };

    /**
     * Creates an empty <code>JFileTree</code> with no root.
     * You shouldn't use this constructor.
     * It's only provided to implement the JavaBean pattern.
     */
    public JFileTree() {
        this(new FileTreeModel());
    }

    /**
     * Creates a new <code>JFileTree</code> which traverses the given
     * root <code>File</code>.
     * The ZipDetector of the given file is used to detect and configure any
     * ZIP compatible files in this directory tree.
     * 
     * @see de.schlichtherle.io.File#getDefaultArchiveDetector()
     * @see de.schlichtherle.io.File#setDefaultArchiveDetector(ArchiveDetector)
     */
    public JFileTree(java.io.File root) {
        this(new FileTreeModel(root));
    }

    /**
     * Creates a new <code>JFileTree</code> which traverses the given
     * {@link FileTreeModel}.
     */
    public JFileTree(FileTreeModel model) {
        super(model);

        setCellRenderer(createTreeCellRenderer());
        addTreeExpansionListener(tel);
    }

    protected TreeCellRenderer createTreeCellRenderer() {
        return new FileTreeCellRenderer(this);
    }

    //
    // Properties.
    //

    /**
     * @throws ClassCastException If <code>model</code> is not an instance
     *         of {@link FileTreeModel}.
     */
    public void setModel(TreeModel model) {
        super.setModel((FileTreeModel) model);
    }

    public void setEditable(final boolean editable) {
        if (editable) {
            super.setEditable(true);
            getCellEditor().addCellEditorListener(cel);
        } else {
            final CellEditor ce = getCellEditor();
            if (ce != null)
                ce.removeCellEditorListener(cel);
            super.setEditable(false);
        }
    }

    private final CellEditorListener cel = new CellEditorListener() {
        public void editingCanceled(ChangeEvent evt) {
        }

        public void editingStopped(ChangeEvent evt) {
            onEditingStopped(evt);
        }
    };

    /**
     * Holds value of property displayingSuffixes.
     */
    private boolean displayingSuffixes = true;

    /**
     * Getter for bound property displayingSuffixes.
     * 
     * @return Value of property displayingSuffixes.
     */
    public boolean isDisplayingSuffixes() {
        return this.displayingSuffixes;
    }

    /**
     * Setter for bound property displayingSuffixes.
     * If this is <code>false</code>, the suffix of files will not be displayed
     * in this tree.
     * Defaults to <code>true</code>.
     * 
     * 
     * @param displayingSuffixes New value of property displayingSuffixes.
     */
    public void setDisplayingSuffixes(boolean displayingSuffixes) {
        boolean oldDisplayingSuffixes = this.displayingSuffixes;
        this.displayingSuffixes = displayingSuffixes;
        firePropertyChange(PROPERTY_DISPLAYING_SUFFIXES,
                oldDisplayingSuffixes, displayingSuffixes);
    }

    /**
     * Holds value of property editingSuffixes.
     */
    private boolean editingSuffixes = true;

    /**
     * Getter for bound property editingSuffixes.
     *
     * @return Value of property editingSuffixes.
     */
    public boolean isEditingSuffixes() {
        return this.editingSuffixes;
    }

    /**
     * Setter for bound property editingSuffixes.
     * If this is <code>false</code>, the suffix of a file will be truncated
     * before editing its name starts.
     * Defaults to <code>true</code>.
     * 
     * @param editingSuffixes New value of property editingSuffixes.
     */
    public void setEditingSuffixes(boolean editingSuffixes) {
        boolean oldEditingSuffixes = this.editingSuffixes;
        this.editingSuffixes = editingSuffixes;
        firePropertyChange(PROPERTY_EDITING_SUFFIXES,
                oldEditingSuffixes, editingSuffixes);
    }

    /**
     * Holds value of property defaultSuffix.
     */
    private String defaultSuffix;

    /**
     * Getter for bound property defaultSuffix.
     *
     * @return Value of property defaultSuffix.
     */
    public String getDefaultSuffix() {
        return this.defaultSuffix;
    }

    /**
     * Setter for bound property defaultSuffixes.
     * Sets the default suffix to use when suffixes are shown and allowed to
     * be edited, but the user did not provide a suffix when editing a file
     * name.
     * This property defaults to <code>null</code> and is ignored for
     * directories.
     *
     * @param defaultSuffix The new default suffix.
     *        If not <code>null</code>, this parameter is fixed to always
     *        start with a <code>'.'</code>.
     */
    public void setDefaultSuffix(String defaultSuffix) {
        final String oldDefaultSuffix = this.defaultSuffix;
        if (defaultSuffix != null) {
            defaultSuffix = defaultSuffix.trim();
            if (defaultSuffix.length() <= 0)
                defaultSuffix = null;
            else if (defaultSuffix.charAt(0) != '.')
                defaultSuffix = "." + defaultSuffix;
        }
        this.defaultSuffix = defaultSuffix;
        firePropertyChange(PROPERTY_DEFAULT_SUFFIX,
                oldDefaultSuffix, defaultSuffix);
    }

    //
    // Editing.
    //

    private java.io.File editedNode;

    /**
     * Returns the node that is currently edited, if any. 
     * This method is not intended for public use - do not use it!
     */
    public java.io.File getEditedNode() {
        return editedNode;
    }

    public boolean isEditing() {
        return editedNode != null;
    }

    public void startEditingAtPath(TreePath path) {
        editedNode = (java.io.File) path.getLastPathComponent();
        super.startEditingAtPath(path);
    }

    public void cancelEditing() {
        editedNode = null;
        super.cancelEditing();
    }

    public boolean stopEditing() {
        final boolean stop = super.stopEditing();
        if (stop)
            editedNode = null;
        return stop;
    }

    /**
     * Called when the editing of a cell has been stopped.
     * The implementation in this class will rename the edited file,
     * obeying the rules for suffix handling and updating the expanded and
     * selected paths accordingly.
     *
     * @param evt The change event passed to
     *        {@link CellEditorListener#editingStopped(ChangeEvent)}.
     */
    protected void onEditingStopped(final ChangeEvent evt) {
        final TreeCellEditor tce = (TreeCellEditor) evt.getSource();
        String base = tce.getCellEditorValue().toString().trim();
        final java.io.File oldNode
                = (java.io.File) getLeadSelectionPath().getLastPathComponent();
        final java.io.File parent = oldNode.getParentFile();
        assert parent != null;
        if (!oldNode.isDirectory()) {
            if (isDisplayingSuffixes() && isEditingSuffixes()) {
                final String suffix = getSuffix(base);
                if (suffix == null) {
                    final String defaultSuffix = getDefaultSuffix();
                    if (defaultSuffix != null)
                        base += defaultSuffix;
                }
            } else {
                final String suffix = getSuffix(oldNode.getName());
                if (suffix != null)
                    base += suffix;
            }
        }
        final java.io.File node = new de.schlichtherle.io.File(parent, base);

        if (!renameTo(oldNode, node))
            Toolkit.getDefaultToolkit().beep();
    }

    private String getSuffix(final String base) {
        final int i = base.lastIndexOf('.');
        return i != -1 ? base.substring(i) : null;
    }

    //
    // Rendering.
    //

    public String convertValueToText(
            final Object value,
            final boolean selected,
            final boolean expanded,
            final boolean leaf,
            final int row,
            final boolean hasFocus) {
        final java.io.File node = (java.io.File) value;
        final java.io.File editedNode = getEditedNode();
        if (node != editedNode && !node.exists()) {
            // You will see this occur for files which have been deleted
            // concurrently or which are returned by File.listFiles(), but do
            // not actually File.exists(), such as "C:\hiberfile.sys" on the
            // Windows platform.
            return "?";
        }

        final String base = node.getName();
        if (base.length() <= 0)
            return node.getPath(); // This is a file system root.
        if (node.isDirectory() ||
                isDisplayingSuffixes()
                && (!node.equals(editedNode) || isEditingSuffixes()))
            return base;
        final int i = base.lastIndexOf('.');
        return i != -1 ? base.substring(0, i) : base;
    }

    //
    // Refreshing.
    //

    /**
     * Refreshes the entire tree,
     * restores the expanded and selected paths and scrolls to the lead
     * selection path if necessary.
     */
    public void refresh() {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath((java.io.File) ftm.getRoot());
        if (path != null)
            refresh(new TreePath[] { path });
    }

    /**
     * Refreshes the subtree for the given node,
     * restores the expanded and selected paths and scrolls to the lead
     * selection path if necessary.
     *
     * @param node The file or directory to refresh.
     *        This may <em>not</em> be <code>null</code>.
     *
     */
    public void refresh(final java.io.File node) {
        if (node == null)
            throw new NullPointerException();

        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path != null)
            refresh(new TreePath[] { path });
    }

    /**
     * Refreshes the subtree for the given paths,
     * restores the expanded and selected paths and scrolls to the lead
     * selection path if necessary.
     *
     * @param paths The array of <code>TreePath</code>s to refresh.
     *        This may be <code>null</code>.
     */
    public void refresh(final TreePath paths[]) {
        if (paths == null || paths.length <= 0)
            return;

        final FileTreeModel ftm = (FileTreeModel) getModel();

        final TreePath lead = getLeadSelectionPath();
        final TreePath anchor = getAnchorSelectionPath();
        final TreePath[] selections = getSelectionPaths();
        
        for (int i = 0, l = paths.length; i < l; i++) {
            final TreePath path = paths[i];
            final Enumeration expansions = getExpandedDescendants(path);
            ftm.refresh((java.io.File) path.getLastPathComponent());
            setExpandedDescendants(expansions);
        }

        setSelectionPaths(selections);
        setAnchorSelectionPath(anchor);
        setLeadSelectionPath(lead);
        scrollPathToVisible(lead);
    }

    private void setExpandedDescendants(final Enumeration expansions) {
        if (expansions == null)
            return;
        while (expansions.hasMoreElements())
            setExpandedState((TreePath) expansions.nextElement(), true);
    }

    //
    // file methods.
    //

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the newly created file
     * is selected and visible.
     * If you would like to create a new file with initial content, please
     * check {@link #copyFrom(de.schlichtherle.io.File, InputStream)}.
     */
    public boolean createNewFile(final java.io.File node) throws IOException {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.createNewFile(node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the newly created directory
     * is selected and visible.
     */
    public boolean mkdir(final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.mkdir(node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the newly created directory
     * is selected and visible.
     */
    public boolean mkdirs(final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.mkdirs(node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the copied node
     * is selected and visible.
     */
    public boolean copyFrom(final de.schlichtherle.io.File node, final InputStream in) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.copyFrom(node, in))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the copied node
     * is selected and visible.
     */
    public boolean copyTo(final de.schlichtherle.io.File oldNode, final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.copyTo(oldNode, node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the recursively copied node
     * is selected and visible.
     */
    public boolean copyAllTo(final de.schlichtherle.io.File oldNode, final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.copyAllTo(oldNode, node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the copied node
     * is selected and visible.
     */
    public boolean archiveCopyTo(final de.schlichtherle.io.File oldNode, final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.archiveCopyTo(oldNode, node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the recursively copied node
     * is selected and visible.
     */
    public boolean archiveCopyAllTo(final de.schlichtherle.io.File oldNode, final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        if (!ftm.archiveCopyAllTo(oldNode, node))
            return false;

        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel},
     * restores the expanded paths, selects <code>node</code> and scrolls to
     * it if necessary.
     */
    public boolean renameTo(final java.io.File oldNode, final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;

        final Enumeration expansions;
        final TreePath oldPath = ftm.getTreePath(oldNode);
        if (oldPath != null)
            expansions = getExpandedDescendants(oldPath);
        else
            expansions = null;

        if (!ftm.renameTo(oldNode, node))
            return false;

        if (expansions != null)
            while (expansions.hasMoreElements())
                setExpandedState(
                        substPath((TreePath) expansions.nextElement(),
                            oldPath, path),
                        true);
        setSelectionPath(path);
        scrollPathToVisible(path);

        return true;
    }

    private TreePath substPath(
            final TreePath tp,
            final TreePath oldPath,
            final TreePath path) {
        final java.io.File file = (java.io.File) tp.getLastPathComponent();
        if (file.equals(oldPath.getLastPathComponent())) {
            return path;
        } else {
            final TreePath parent = substPath(tp.getParentPath(), oldPath, path);
            return parent.pathByAddingChild(
                    new de.schlichtherle.io.File((java.io.File) parent.getLastPathComponent(),
                             file.getName()));
        }
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the successor to the deleted node
     * is selected and visible.
     */
    public boolean delete(final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;
        scrollPathToVisible(path);
        final int row = getRowForPath(path);

        if (!ftm.delete(node))
            return false;

        setSelectionRow(row);

        return true;
    }

    /**
     * Forwards the call to the {@link FileTreeModel}
     * and scrolls the tree so that the successor to the deleted node
     * is selected and visible.
     */
    public boolean deleteAll(final de.schlichtherle.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path == null)
            return false;
        scrollPathToVisible(path);
        final int row = getRowForPath(path);

        if (!ftm.deleteAll(node))
            return false;

        setSelectionRow(row);

        return true;
    }

    public void setSelectionNode(final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path != null) {
            setSelectionPath(path);
        }
    }

    public void setSelectionNodes(final java.io.File[] nodes) {
        final FileTreeModel ftm = (FileTreeModel) getModel();

        final List list = new LinkedList();
        TreePath lastPath = null;
        for (int i = 0, l = nodes.length; i < l; i++) {
            lastPath = ftm.getTreePath(nodes[i]);
            if (lastPath != null)
                list.add(lastPath);
        }

        final int size = list.size();
        if (size > 0) {
            final TreePath[] paths = new TreePath[size];
            list.toArray(paths);
            setSelectionPaths(paths);
        }
    }

    public void scrollNodeToVisible(final java.io.File node) {
        final FileTreeModel ftm = (FileTreeModel) getModel();
        final TreePath path = ftm.getTreePath(node);
        if (path != null) {
            scrollPathToVisible(path);
        }
    }
}

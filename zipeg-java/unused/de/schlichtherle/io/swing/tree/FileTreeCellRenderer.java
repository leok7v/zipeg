/*
 * FileTreeCellRenderer.java
 *
 * Created on 13. Februar 2006, 15:45
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

package de.schlichtherle.io.swing.tree;

import de.schlichtherle.io.swing.FileSystemView;
import de.schlichtherle.io.swing.JFileTree;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * A {@link javax.swing.tree.TreeCellRenderer} which uses an instance of
 * {@link FileSystemView} to display the system icon for each node in a
 * {@link JFileTree} wherever possible.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 5.1
 */
public class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final FileSystemView fsv = (FileSystemView) FileSystemView.getFileSystemView();

    private final JFileTree fileTree;
    
    public FileTreeCellRenderer(final JFileTree fileTree) {
        this.fileTree = fileTree;
    }

    public Icon getOpenIcon() {
        // This is a hack: When editing a node, the edited node is the ONLY
        // one for which the BasicTreeUI somehow BYPASSES the call to
        // getTreeCellRendererComponent(*) and calls this method directly in
        // order to determine the icon to draw for the edited node.
        // So we simply return the icon for the edited node for ALL nodes
        // while editing and rely on getTreeCellRendererComponent(*) to fix
        // the icon for all other nodes than the edited one.
        final java.io.File node = fileTree.getEditedNode();
        if (node != null)
            return fsv.getSystemIcon(node);
        else
            return super.getOpenIcon();
    }

    public Icon getClosedIcon() {
        // This is a hack: When editing a node, the edited node is the ONLY
        // one for which the BasicTreeUI somehow BYPASSES the call to
        // getTreeCellRendererComponent(*) and calls this method directly in
        // order to determine the icon to draw for the edited node.
        // So we simply return the icon for the edited node for ALL nodes
        // while editing and rely on getTreeCellRendererComponent(*) to fix
        // the icon for all other nodes than the edited one.
        final java.io.File node = fileTree.getEditedNode();
        if (node != null)
            return fsv.getSystemIcon(node);
        else
            return super.getClosedIcon();
    }

    public Icon getLeafIcon() {
        // This is a hack: When editing a node, the edited node is the ONLY
        // one for which the BasicTreeUI somehow BYPASSES the call to
        // getTreeCellRendererComponent(*) and calls this method directly in
        // order to determine the icon to draw for the edited node.
        // So we simply return the icon for the edited node for ALL nodes
        // while editing and rely on getTreeCellRendererComponent(*) to fix
        // the icon for all other nodes than the edited one.
        final java.io.File node = fileTree.getEditedNode();
        if (node != null)
            return fsv.getSystemIcon(node);
        else
            return super.getLeafIcon();
    }

    public Component getTreeCellRendererComponent(
            final JTree tree,
            final Object value,
            final boolean selected,
            final boolean expanded,
            final boolean leaf,
            final int row,
            final boolean hasFocus) {
        super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus);

        setIcon(fsv.getSystemIcon((java.io.File) value));

        return this;
    }
}

package com.zipeg;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.io.*;

@SuppressWarnings({"unchecked"})
public final class DirectoryTree extends JTree {

    private final DefaultTreeCellRenderer uicr;
    private Color selection;
    private Color dimmed;

    private final TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) { updateStatusBar(); }
    };

    private final FocusListener focusListener = new FocusAdapter() {
        public void focusGained(FocusEvent e) {
            updateStatusBar();
            getContentPane().setLastFocused(DirectoryTree.this);
            repaint();
        }
        public void focusLost(FocusEvent e) {
            repaint();
        }
    };

    DirectoryTree() {
        super(new DefaultTreeModel(null)); // new DefaultMutableTreeNode()?
        uicr = (DefaultTreeCellRenderer)getCellRenderer();
        if (Util.isWindows) {
            uicr.setClosedIcon(new ImageIcon(Resources.getImage("win.folder.xp.16x16")));
            uicr.setOpenIcon(new ImageIcon(Resources.getImage("win.open.xp.16x16")));
            uicr.setLeafIcon(uicr.getClosedIcon());
        } else {
/*          Seems to be not needed:
            uicr.setClosedIcon(new ImageIcon(Resources.getImage("mac.folder.16x16")));
            uicr.setOpenIcon(new ImageIcon(Resources.getImage("mac.open.16x16")));
*/
            uicr.setLeafIcon(uicr.getDefaultClosedIcon());
        }
        /*
        saveIcon(uicr.getOpenIcon(), "mac.open.16x16.png");
        saveIcon(uicr.getClosedIcon(), "mac.folder.16x16.png");
        */
        setCellRenderer(new CellRenderer());
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setRootVisible(false);
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
        if (Util.isMac) {
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, Event.META_MASK), "up");
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, Event.META_MASK), "up");
        }
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, Event.ALT_MASK), "up");
        getActionMap().put("enter", new AbstractAction(){
            public void actionPerformed(ActionEvent e) { enter(); }
        });
        getActionMap().put("up", new AbstractAction(){
            public void actionPerformed(ActionEvent e) { up(); }
        });
        setDropTarget(new DropTarget());
    }

/*
    private static void saveIcon(Icon icon, String name) {
        {
            BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            Graphics g = bi.getGraphics();
            icon.paintIcon(new JComponent(){}, g, 0, 0);
            g.dispose();
            try {
                ImageIO.write(bi, "png", new File("/Users/leo/Desktop/" + name));
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }
*/

    protected void setExpandedState(TreePath path, boolean state) {
        if (Zipeg.getArchive() != null && !Zipeg.inProgress()) {
            super.setExpandedState(path, state);
        }
    }

    private void enter() {
        if (Zipeg.getArchive() != null && !Zipeg.inProgress()) {
            TreeNode node = (TreeNode)getLastSelectedPathComponent();
            if (node != null) {
                expandPath(getSelectionModel().getSelectionPath());
                Actions.postEvent("enterDirectory", node);
            }
        }
    }

    private void up() {
        if (Zipeg.getArchive() != null && !Zipeg.inProgress()) {
            TreePath path = getSelectionModel().getSelectionPath();
            TreePath parent = path == null ? null : path.getParentPath();
            if (parent != null) {
                getSelectionModel().setSelectionPath(parent);
            }
        }
    }

    public void addNotify() {
        super.addNotify();
        Actions.addListener(this);
        addFocusListener(focusListener);
        addTreeSelectionListener(treeSelectionListener);
    }

    public void removeNotify() {
        removeTreeSelectionListener(treeSelectionListener);
        removeFocusListener(focusListener);
        Actions.removeListener(this);
        super.removeNotify();
    }

    public Insets getInsets() {
        return new Insets(4, 4, 4, 4);
    }

    public Dimension getMinimumSize() {
        return new Dimension(140, 100);
    }

    private ContentPane getContentPane() {
        MainFrame mf = MainFrame.getInstance();
        return mf == null ? null : (ContentPane)mf.getContentPane();
    }

    private boolean isLastFocused() {
        return hasFocus() || getContentPane() != null && getContentPane().getLastFocused() == this;
    }

    public List getSelected() {
        DefaultTreeModel model = (DefaultTreeModel)getModel();
        TreePath path = getSelectionPath();
        if (path != null) {
            TreeNode selected = (TreeNode)path.getLastPathComponent();
            if (selected != model.getRoot()) {
                List list = new LinkedList();
                ((TreeElement)selected).collectDescendants(list);
                return list;
            }
        }
        return null;
    }

    public void extractionCompleted(Object params) { // {error, quit} or {null, quit}
        if (isLastFocused()) {
            updateStatusBar();
            requestFocusInWindow();
        }
    }

    public void windowGainedFocus(Object event) {
        if (isLastFocused()) {
            requestFocusInWindow();
        }
    }

    public void windowLostFocus(Object event) {
    }

    public void updateCommandState(Map m) {
        if (isRootVisible()) {
            m.put("commandActionsExtract", Boolean.valueOf(!Dialogs.isShown()));
            m.put("commandActionsAdd", Boolean.valueOf(!Dialogs.isShown()));
            m.put("commandFileClose", Boolean.valueOf(!Dialogs.isShown()));
            m.put("commandFilePrint", Boolean.valueOf(!Dialogs.isShown()));
        }
        TreeNode node = (TreeNode)getLastSelectedPathComponent();
        if (node != null && node.getParent() != null) {
            m.put("commandGoEnclosingFolder", Boolean.valueOf(!Dialogs.isShown()));
        }
    }

    private void updateStatusBar() {
        MainFrame mf = MainFrame.getInstance();
        if (Zipeg.inProgress() || mf == null ||
           !mf.isVisible() ||
            Zipeg.getArchive() == null ||
           !Zipeg.getArchive().isOpen()) {
            return;
        }
        // TODO: (Leo) and even after that the archive async closing
        // can still be racing the status bar. Think of it and make sure
        // archive is never closed from the background thread.
        TreeNode node = (TreeNode)getLastSelectedPathComponent();
        TreeElement element = node == null ? null : (TreeElement)node;
        File file = element == null ? null : new File(element.getFile());
        if (file != null) {
            Actions.postEvent("setStatus", file.getName());
            long bytes = element.getDescendantSize();
            long files = element.getDescendantFileCount();
            long dirs  = element.getDescendantDirectoryCount();
            String v = Util.formatMB(bytes);
            String s = (dirs <= 1 ? "" : Util.formatNumber(dirs) + " folders  ") +
                       (files <= 1 ? "" : Util.formatNumber(files) + " items  ") + v;
            Actions.postEvent("setInfo", s);
        }
    }

    public void selectFolder(Object treeElement) {
        if (Zipeg.getArchive() != null && !Zipeg.inProgress()) {
            TreePath path = getSelectionPath();
            if (path != null) {
                // do not request focus if child is selected
                TreeNode selected = (TreeNode)path.getLastPathComponent();
                for (int i = 0; i < selected.getChildCount(); i++) {
                    TreeNode child = selected.getChildAt(i);
                    if (child == treeElement) {
                        path = path.pathByAddingChild(child);
                        setSelectionPath(path);
                        scrollPathToVisible(path);
                        return;
                    }
                }
                requestFocusInWindow();
                path = path.getParentPath();
                while (path != null) {
                    TreeNode parent = (TreeNode)path.getLastPathComponent();
                    if (parent == treeElement) {
                        setSelectionPath(path);
                        scrollPathToVisible(path);
                        return;
                    }
                    path = path.getParentPath();
                }
            }
        }
    }

    public void archiveOpened(Object param) {
        assert IdlingEventQueue.isDispatchThread();
        assert param != null;
        MainFrame mf = MainFrame.getInstance();
        if (mf != null) {
            Archive a = (Archive)param;
            setModel(new DefaultTreeModel(a.getRoot()));
            setRootVisible(true);
            mac.setDocumentFile(mf.getRootPane(), new File(a.getName()));
            mac.setDocumentModified(mf.getRootPane(), false);
            if (a.isNested()) {
                mf.setTitle(a.getParentName() + " [" + new File(a.getName()).getName() + "]");
            } else {
                String filename = stripMultipart(a.getName());
                mf.setTitle(filename);
            }
            Util.invokeLater(200, new Runnable(){
                public void run() {
                    setSelectionInterval(0, 0);
                    requestFocusInWindow();
                }
            });
        }
    }

    private static String stripMultipart(String s) {
        int ix = s.toLowerCase().indexOf(".part1.rar");
        if (ix > 0) {
            s = s.substring(0, ix) + ".*.rar";
        }
        return s;
    }

    public void commandFileClose() {
        getSelectionModel().clearSelection();
        setModel(new DefaultTreeModel(null));
        MainFrame mf = MainFrame.getInstance();
        if (mf != null) {
            mf.setTitle(null);
        }
        setRootVisible(false);
    }

    private class CellRenderer implements TreeCellRenderer {

        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            JLabel cr = (JLabel)uicr.getTreeCellRendererComponent(tree, value, selected,
                                                                  expanded, leaf, row, focused);
            if (leaf && selected) {
                cr.setIcon(uicr.getOpenIcon());
            }
            if (selected) {
                if (dimmed == null) {
                    selection = uicr.getBackgroundSelectionColor();
                    dimmed = new Color(selection.getRed(),
                                       selection.getGreen(),
                                       selection.getBlue(),
                                       40);
                }
                uicr.setBackgroundSelectionColor(isLastFocused() ? selection : dimmed);
                if (!isLastFocused()) {
                    uicr.setForeground(uicr.getTextNonSelectionColor());
                }
            }
            return cr;
        }

    }

}

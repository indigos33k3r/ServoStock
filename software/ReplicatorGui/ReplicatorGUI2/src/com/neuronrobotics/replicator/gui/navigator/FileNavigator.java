package com.neuronrobotics.replicator.gui.navigator;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;


import javax.swing.DropMode;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class FileNavigator extends JTree implements TreeSelectionListener, MouseListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 5148061626146938126L;
	
	//private File mainDirectory;
	private FileNavigatorFolderNode theRoot;
	private DefaultMutableTreeNode currentNode;
		
	private LinkedList<FileNavigatorListener> theDirectoryTreeListeners;
	
	private static FilenameFilter dirFilter = new FilenameFilter(){

		@Override
		public boolean accept(File arg0, String arg1) {
			File testF = new File(arg0.getAbsolutePath()+"\\"+arg1);
			return testF.isDirectory();
		}
		
	};
	
	private static FilenameFilter stlfilter = new FilenameFilter(){
		@Override
		public boolean accept(File arg0, String arg1) {
			File newf = new File(arg0,arg1);
			return !newf.isDirectory()&&arg1.endsWith(".stl");
		}

	};	
	
	public static FileNavigator getDirectoryTree(File mainDirectory){
		
		if(!mainDirectory.exists())	mainDirectory.mkdir();
		if(!mainDirectory.isDirectory()) throw new IllegalArgumentException(); 
			
		FileNavigatorFolderNodeObject rootObject = new FileNavigatorFolderNodeObject(mainDirectory);
		FileNavigatorFolderNode root = new FileNavigatorFolderNode(rootObject);
								
		DefaultTreeModel dtm = new DefaultTreeModel(root);
		FileNavigator theDirectoryTree = new FileNavigator(dtm);
				
		theDirectoryTree.theRoot = root;
		
		addDirectoryContents(dtm, root);
				
		TreePath tp = new TreePath(root.getPath());
		theDirectoryTree.setExpandedState(tp, true);
		
		theDirectoryTree.setRootVisible(false);
		
		theDirectoryTree.treeDidChange();
		
		theDirectoryTree.setTransferHandler(new FileNavigatorTransferHandler(theDirectoryTree));
		return theDirectoryTree; 
		
	}
		
	private static void addDirectoryContents(DefaultTreeModel dtm,FileNavigatorFolderNode fn){
		int ct=0;
		
		File mainDir = ((FileNavigatorFolderNodeObject)fn.getUserObject()).getTheDirectory();
		
		File[] directoryFiles = mainDir.listFiles(dirFilter);
		
		File[] stlFiles = mainDir.listFiles(stlfilter);
		
		for(File d:directoryFiles){
			if(d.getName().contains(".svn")) continue; //Ignore .svn folder when testing
			FileNavigatorFolderNodeObject tempObj = new FileNavigatorFolderNodeObject(d);
			FileNavigatorFolderNode newNode = new FileNavigatorFolderNode(tempObj);
			dtm.insertNodeInto(newNode, fn, ct++);
			addDirectoryContents(dtm,newNode);
		}
		
		for(File stl:stlFiles){
			FileNavigatorLeafNodeObject tempObj = new FileNavigatorLeafNodeObject(stl);
			FileNavigatorLeafNode newNode = new FileNavigatorLeafNode(tempObj);
			dtm.insertNodeInto(newNode, fn, ct++);
		}
	}
	
	private FileNavigator(DefaultTreeModel tm){
		super(tm);
		addTreeSelectionListener(this);
		addMouseListener(this);
		this.setDragEnabled(true);
		
		this.setDropMode(DropMode.ON);
		currentNode = null;
		theDirectoryTreeListeners = new LinkedList<FileNavigatorListener>();	
	}
				
	/*
	public void linkNewFolder(String newFullFileName){
		linkNewFolder(theRoot,newFullFileName);
	}
	
	public void linkNewFolder(DefaultMutableTreeNode parent,String newFullFileName){
	
		
	}
	 */
	
	public void addNewFolder(String folderName) throws FileNotDirectoryException, IOException{
		addNewFolder(theRoot,folderName);		
	}
	
	public void addNewFolder(FileNavigatorFolderNode parent, String folderName) throws FileNotDirectoryException, IOException{
		String newName = ((FileNavigatorFolderNodeObject)(parent.getUserObject())).getTheDirectory().getAbsolutePath()+"\\"+folderName;
		File newFolder = new File(newName);
		if(newFolder.exists()&&!newFolder.isDirectory()) throw new FileNotDirectoryException();
		else if(!newFolder.exists()) {
			newFolder.mkdir();
		}
		
		FileNavigatorFolderNodeObject newObject = new FileNavigatorFolderNodeObject(newFolder);
		FileNavigatorFolderNode newNode = new FileNavigatorFolderNode(newObject);
		
				
		((DefaultTreeModel)treeModel).insertNodeInto(newNode, parent, parent.getChildCount());
	
	}
	
	public void copyInSTLFile(String name) throws IOException{
		File stl = new File(name);
		copyInSTLFile(theRoot,stl);
	}
	
	public void copyInSTLFile(File stl) throws IOException{
		copyInSTLFile(theRoot,stl);
	}
	
	public void copyInSTLFile(FileNavigatorFolderNode parent, File stl) throws IOException{
		
		String pathName = ((FileNavigatorFolderNodeObject)parent.getUserObject()).getTheDirectory().getAbsolutePath()+"\\"+stl.getName();
		
		Path outPath = FileSystems.getDefault().getPath(pathName);
		
		Path inPath = stl.toPath();
		
		Files.copy(inPath, outPath);	
		
		File newSTL = outPath.toFile();
				
		File gcode = new File(stl.getAbsolutePath()+".gcode");
						
		FileNavigatorLeafNodeObject newObject = new FileNavigatorLeafNodeObject(newSTL,gcode);
		
		FileNavigatorLeafNode newChild = new FileNavigatorLeafNode(newObject);
		
		((DefaultTreeModel)this.treeModel).insertNodeInto(newChild, parent, parent.getChildCount());
	}
	
	public File getSelectedSTLFile() throws NullPointerException{
		if(currentNode==null) throw new NullPointerException();
		Object obj = currentNode.getUserObject();		
		if(!obj.getClass().equals(FileNavigatorLeafNodeObject.class)) return null;
		
		return ((FileNavigatorLeafNodeObject)obj).getTheSTLFile();
	}
	
	public File getSelectedGCodeFile() throws NullPointerException{
		if(currentNode==null) throw new NullPointerException();
		Object obj = currentNode.getUserObject();		
		if(!obj.getClass().equals(FileNavigatorLeafNodeObject.class)) return null;
		
		return ((FileNavigatorLeafNodeObject)obj).getTheGCodeFile();
	}

	@Override
	public void valueChanged(TreeSelectionEvent arg0) {
		currentNode = (DefaultMutableTreeNode)arg0.getPath().getLastPathComponent();
		if(currentNode.getClass().equals(FileNavigatorLeafNode.class)){
			notifyListenersLeafSelected();
		} else if(currentNode.getClass().equals(FileNavigatorFolderNode.class)){
			notifyListenersFolderSelected();
		} 
	}
	
	private void notifyListenersLeafSelected() {
		for(FileNavigatorListener dtl:this.theDirectoryTreeListeners) dtl.alertDirectoryLeafSelected();	
	}
		
	private void notifyListenersFolderSelected() {
		for(FileNavigatorListener dtl:this.theDirectoryTreeListeners) dtl.alertDirectoryFolderSelected();	
	}

	private void notifyListenersLeafDoubleClicked() {
		for(FileNavigatorListener dtl:this.theDirectoryTreeListeners) dtl.alertDirectoryLeafDoubleClicked();	
	}
	
	private void notifyListenersFolderDoubleClicked() {
		for(FileNavigatorListener dtl:this.theDirectoryTreeListeners) dtl.alertDirectoryFolderDoubleClicked();	
	}

	public void addDirectoryTreeListener(FileNavigatorListener dtl){
		if(!this.theDirectoryTreeListeners.contains(dtl)) theDirectoryTreeListeners.add(dtl);
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		if(arg0.getClickCount()>=2&&this.currentNode!=null){
			//System.out.println("Double click detected");
			if(currentNode.getClass().equals(FileNavigatorLeafNode.class)){
				notifyListenersLeafDoubleClicked();
			//	System.out.println("Leaf double click notified");
			}
			else if (currentNode.getClass().equals(FileNavigatorFolderNode.class)) notifyListenersFolderDoubleClicked();
		}
		
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
			
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		
	}

}
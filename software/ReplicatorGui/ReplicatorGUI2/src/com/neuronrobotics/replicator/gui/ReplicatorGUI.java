package com.neuronrobotics.replicator.gui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;

import com.neuronrobotics.replicator.gui.navigator.FileNavigator;
import com.neuronrobotics.replicator.gui.navigator.FileNavigatorListener;import com.neuronrobotics.replicator.gui.preview.STLPreviewPanel;
import com.neuronrobotics.replicator.gui.stl.ASCIISTLWriter;
import com.neuronrobotics.replicator.gui.stl.STLLoader;
import com.neuronrobotics.replicator.gui.stl.STLObject;

public class ReplicatorGUI extends JFrame implements GUIFrontendInterface, FileNavigatorListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7003760875384715160L;
	
	private File defaultWorkspaceDirectory;

	private Container toolbarContainer;
	private Container leftContainer, bottomContainer;

	private STLPreviewPanel previewContainer;
	//private STLPreviewPanel previewPanel;

	private JTabbedPane leftTab;

	private JMenuBar menuBar;
	
	private JToolBar mainToolbar;
	private JMenu fileMenu, editMenu, helpMenu;

	private JButton printButton;
	private JButton cancelButton;
	private JButton connectButton;

	private FileNavigator theDirectoryTree;

	private JMenuItem openFileItem;
	private JMenuItem newWorkspaceItem;
	private JMenuItem importSTLItem;

	private JLabel statusLabel;
	private JProgressBar printProgress;

	private GUIBackendInterface theGUIDriver;
	
	private static File defaultWorkspaceSTL = new File("testWorkspaceSTL.stl");
	
	public ReplicatorGUI(GUIBackendInterface theGUIDriver) {
		super();
		this.theGUIDriver = theGUIDriver;
		this.theGUIDriver.setFrontend(this);
		defaultWorkspaceDirectory = new File("DefaultWorkspaceFolder");

		initialize();	
	}
	
	public void requestValidate(){
		this.invalidate();
		this.validate();
	}
	
	public ReplicatorGUI(GUIBackendInterface theGUIDriver, File mainDirectory) {
		super();
		this.theGUIDriver = theGUIDriver;
		this.theGUIDriver.setFrontend(this);

		defaultWorkspaceDirectory = mainDirectory;
		
		initialize();
	}

	private void initialize() {
			
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();

		setFont(new Font("Helvetica", Font.PLAIN, 14));
		setLayout(gridbag);
				
		toolbarContainer = new Container();
		toolbarContainer.setLayout(new GridLayout(1, 1));

		leftContainer = new Container();
		leftContainer.setLayout(new GridLayout(1, 1));

		leftTab = new JTabbedPane();
		leftContainer.add(leftTab);

		previewContainer = new STLPreviewPanel(this);

		bottomContainer = new Container();
		bottomContainer.setLayout(new GridLayout(2, 1));

		Dimension d = leftContainer.getPreferredSize();
		d.width = 225;
		leftContainer.setPreferredSize(d);
		
		d = bottomContainer.getPreferredSize();
		d.height=50;
		bottomContainer.setPreferredSize(d);

		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;

		c.gridwidth = GridBagConstraints.REMAINDER;
		gridbag.setConstraints(toolbarContainer, c);
		add(toolbarContainer);

		c.weightx = 0.0;
		c.gridheight = GridBagConstraints.RELATIVE;
		c.gridwidth = GridBagConstraints.RELATIVE;
		gridbag.setConstraints(leftContainer, c);
		add(leftContainer);

		c.weightx = 1.0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weighty = 1.0;
		gridbag.setConstraints(previewContainer, c);
		this.add(previewContainer);
		
		//gridbag.setConstraints(previewPanel, c);
		//this.add(previewPanel);

		c.weighty = 0.0;
		c.gridheight = 1;// GridBagConstraints.REMAINDER;
		gridbag.setConstraints(bottomContainer, c);
		this.add(bottomContainer);

		// //////

		menuBar = new JMenuBar();
		fileMenu = new JMenu("File");
		editMenu = new JMenu("Edit");
		helpMenu = new JMenu("Help");
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(helpMenu);

		openFileItem = new JMenuItem("Open File...");
		openFileItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser fileChooser = new JFileChooser();
				int returnVal = fileChooser.showOpenDialog(new JDialog());
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					System.out.println("You chose to open this file: "
							+ fileChooser.getSelectedFile().getName());
					File newFile = fileChooser.getSelectedFile();
					try {
						addPreview(newFile);

					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("IO Exception dun happen'd");
					}
				}
			}

		});
		
		this.newWorkspaceItem = new JMenuItem("New Workspace");
		newWorkspaceItem.addActionListener(new ActionListener(){
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String folderName = JOptionPane.showInputDialog("New Workspace Name: ");
				
				//String err = null;
				
				//try {
					//theDirectoryTree.addNewFolder(folderName);
					previewContainer.addEmptyWorkspace(folderName,defaultWorkspaceSTL);
			//	} catch (FileNotDirectoryException e) {
					//err = "That file exists and it is not a directory.";
				//	e.printStackTrace();
			//	} catch (IOException e) {
				//	err = "IO Exception occured when adding new folder.";
				//	e.printStackTrace();
				//} finally{
				//	if(err!=null) errorDialog(err);
				//}
			}
		});
		
		importSTLItem = new JMenuItem("Import STL");
		importSTLItem.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser fileChooser = new JFileChooser("*.stl");
				int returnVal = fileChooser.showOpenDialog(new JDialog());
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					System.out.println("You chose to import this file: "
							+ fileChooser.getSelectedFile().getName());
					File newFile = fileChooser.getSelectedFile();
					try {
						theDirectoryTree.copyInSTLFile(newFile);
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println("IO Exception dun happen'd");
					}
				}	
			}
			
		});
		
		fileMenu.add(openFileItem);
		fileMenu.add(newWorkspaceItem);
		fileMenu.add(importSTLItem);
		
		this.setJMenuBar(menuBar);

		//menuContainer.add(menuBar);

		mainToolbar = new JToolBar();
		printButton = new JButton("PRINT");
		printButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				toolbarPrintButtonHandler();
			}
		});
		cancelButton = new JButton("CANCEL");
		cancelButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancelButtonHandler();
			}
		});

		connectButton = new JButton("CONNECT");
		connectButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				connectButtonHandler();
			}

		});

		mainToolbar.add(printButton);
		mainToolbar.add(cancelButton);
		mainToolbar.add(connectButton);

		toolbarContainer.add(mainToolbar);

		theDirectoryTree = FileNavigator.getDirectoryTree(defaultWorkspaceDirectory);
				
		theDirectoryTree.addDirectoryTreeListener(this);
		
		leftTab.add("Navigator",theDirectoryTree);

		printProgress = new JProgressBar();

		statusLabel = new JLabel("Welcome (Printer Not Connected)");

		bottomContainer.add(statusLabel);
		bottomContainer.add(printProgress);
				
		/////
		
		this.addWindowListener(new WindowListener(){

			@Override
			public void windowActivated(WindowEvent arg0) {
				}

			@Override
			public void windowClosed(WindowEvent arg0) {	
			}

			@Override
			public void windowClosing(WindowEvent arg0) {
				arg0.getWindow().dispose();
				System.exit(0);
			}

			@Override
			public void windowDeactivated(WindowEvent arg0) {
			}

			@Override
			public void windowDeiconified(WindowEvent arg0) {
			}

			@Override
			public void windowIconified(WindowEvent arg0) {
			}

			@Override
			public void windowOpened(WindowEvent arg0) {
			}
			});
		

	}

	public void workspaceNavigatorPrintHandler() {
	}

	public void toolbarPrintButtonHandler() {
		
		
		if (previewContainer.hasNoPreviews()) {
			this.statusLabel.setText("Nothing to print");
			this.errorDialog("Nothing to print!");
			return;
		}
		/*
		if (previewPanel.hasNoPreviews()) {
			this.statusLabel.setText("Nothing to print");
			this.errorDialog("Nothing to print!");
			return;
		}*/
		
		
		PrintOptionsDialog testD = new PrintOptionsDialog(this);
		
		testD.setVisible(true);
		
		PrintOptions theOptions = testD.getPrintOptions();	
		
		if(theOptions==null){
			this.statusLabel.setText("Print Aborted");
			return;
		}
		
		boolean reSlice = theOptions.isForceReslice();		
		boolean rewrite = theOptions.isUseTransformedSTL();//this.userPrompt("Rewrite STL with current Transforms?");
		
		File currentFile = new File("Temp\\tempTransform.stl");
		System.out.println(rewrite);
		
			currentFile = new File("Temp\\tempTransform.stl");
			//currentFile.delete();
			if(!currentFile.exists())
				try {
					currentFile.createNewFile();
				} catch (IOException e) {
					this.statusLabel.setText("Rewrite failed");
					this.errorDialog("Rewrite failed!");
					e.printStackTrace();
					return;
				}
		if (rewrite) {
			System.out.println("Rewriting...");
			STLObject tranObj = previewContainer.getCurrentTab().getTheMotherFuckingModel().getMergedSTLObject();
					
					//getCurrentPreview().getMergedSTLObject(); .getTransformedSTLObject(previewContainer.getCurrentPreview().getTransform3D());
			// STLObject tranObj =
			// previewPanel.getCurrentPreview().getSTLObject().getTransformedSTLObject(previewPanel.getCurrentPreview().getTransform3D());
			System.out.println("Here");
			ASCIISTLWriter aw = new ASCIISTLWriter(tranObj);
			try {
				this.statusLabel.setText("Rewriting new STL");
				System.out.println("And here");
				aw.writeSTLToFile(currentFile);
				System.out.println("Finished rewriting");
			} catch (IOException e) {
				this.statusLabel.setText("Rewrite failed. Aborting print.");
				this.errorDialog("Rewrite failed!");
				e.printStackTrace();
				return;
			} 
			
		}
		
		if(!rewrite){ 
			return;};
		File currentGCodeFile = new File("Temp\tempTransform.gcode");
		
		//if(!rewrite) currentFile = previewPanel.getCurrentSTLFile();
		//File currentGCodeFile = previewPanel.getCurrentGCodeFile();
		
		boolean isPrinting;
		if (reSlice)
			isPrinting= theGUIDriver.requestPrint(currentFile, currentGCodeFile);
		else
			isPrinting = theGUIDriver.requestPrint(currentGCodeFile);
		
		
		if(isPrinting) System.out.println("Printing file " + currentFile.getAbsolutePath());
	}

	public void cancelButtonHandler() {
		theGUIDriver.requestCancel();
	}

	private void connectButtonHandler() {
		theGUIDriver.connectPrinter();
	}

	public boolean addPreview(File f) throws Exception {
		File gcode = new File(f.getAbsolutePath() + ".gcode");	
		return this.addPreview(f,gcode);
	}

	public boolean addPreview(File stl, File gcode) throws Exception {
		if(gcode==null) gcode = new File("Temp\\temp.gcode");
		if(!gcode.exists()) gcode.createNewFile();
		
		return this.previewContainer.addPreview(stl, gcode, defaultWorkspaceSTL);
		
	}
	
	@Override
	public boolean userPrompt(String prompt) {
		return (JOptionPane.showConfirmDialog(this, prompt, "",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) ? true
				: false;
	}

	@Override
	public void errorDialog(String errorMessage) {
		JOptionPane.showMessageDialog(this, errorMessage, "Error",
				JOptionPane.ERROR_MESSAGE);
	}
	
	@Override
	public void alertStatusUpdated() {
		this.statusLabel.setText(theGUIDriver.getStatusString());
		if (theGUIDriver.getCurrentDriverState() == GUIBackendInterface.DriverState.SLICING)
			this.printProgress.setOrientation(theGUIDriver.getSliceProgress());
		else
			this.printProgress.setOrientation(theGUIDriver.getPrintProgress());
	}

	@Override
	public boolean errorDialogWithFix(String errorMessage, String fixPrompt) {
		int op = JOptionPane.showConfirmDialog(this,errorMessage+"\n\n"+fixPrompt,"Error",JOptionPane.YES_NO_OPTION);
		return op==JOptionPane.YES_OPTION;
	}

	@Override
	public void alertDirectoryLeafSelected() {
		
	}

	@Override
	public void alertDirectoryFolderSelected() {
		
	}

	@Override
	public void alertDirectoryLeafDoubleClicked() {
		try {
			if(!this.previewContainer.hasNoPreviews()){
				STLObject newSTL = STLLoader.loadFile(theDirectoryTree.getSelectedSTLFile());
				this.previewContainer.getCurrentTab().getTheMotherFuckingModel().addSTLObject(newSTL);
			} else  this.addPreview(theDirectoryTree.getSelectedSTLFile(), theDirectoryTree.getSelectedGCodeFile());
		} catch (Exception e) {
			this.errorDialog("Unknown IO Error Loading Preview");
			e.printStackTrace();
		}	
	}

	@Override
	public void alertDirectoryFolderDoubleClicked() {
		
	}

}

package com.neuronrobotics.replicator.main;

import java.awt.Frame;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.neuronrobotics.replicator.gui.DirectorySelectionDialog;
import com.neuronrobotics.replicator.gui.GUIDriver;
import com.neuronrobotics.replicator.gui.ReplicatorGUI;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class Main {
	
	private static ImageIcon imageIcon = new ImageIcon("Images\\hat.png");

	public static void main(String[] argv) {
		
		//Setting look and Feel
		String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
		try {
			UIManager.setLookAndFeel(lookAndFeel);
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		} catch (InstantiationException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		} catch (UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		

		JDialog.setDefaultLookAndFeelDecorated(true);
		
		File defaultDir = new File("DefaultWorkspaceFolder");
		
		DirectorySelectionDialog dialog = new DirectorySelectionDialog(new Frame(),defaultDir);
		dialog.setIconImage(imageIcon.getImage());
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setVisible(true);
		while(dialog.isDisplayable()){
			ThreadUtil.wait(100);
		}
		
		if(dialog.getTheFile()!=null){
			startGUI(dialog.getTheFile());
		} else {
			System.exit(0);
		}
		
	}
	
	private static void startGUI(File directory){
		
		ReplicatorGUI theGUI;
		if(!directory.exists()||!directory.isDirectory()) theGUI = new ReplicatorGUI(new GUIDriver());
		else theGUI = new ReplicatorGUI(new GUIDriver(),directory);
		
		theGUI.setIconImage(imageIcon.getImage());
		theGUI.setTitle("Replicator");
		theGUI.setSize(1000,600);
		theGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
		theGUI.setVisible(true);
		
	}	
	
}
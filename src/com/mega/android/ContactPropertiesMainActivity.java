package com.mega.android;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.ResourceBundle.Control;

import com.mega.android.FileStorageActivity.Mode;
import com.mega.android.utils.Util;
import com.mega.components.EditTextCursorWatcher;
import com.mega.components.RoundedImageView;
import com.mega.sdk.MegaApiAndroid;
import com.mega.sdk.MegaApiJava;
import com.mega.sdk.MegaError;
import com.mega.sdk.MegaGlobalListenerInterface;
import com.mega.sdk.MegaNode;
import com.mega.sdk.MegaRequest;
import com.mega.sdk.MegaRequestListenerInterface;
import com.mega.sdk.MegaTransfer;
import com.mega.sdk.MegaTransferListenerInterface;
import com.mega.sdk.MegaUser;
import com.mega.sdk.NodeList;
import com.mega.sdk.TransferList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class ContactPropertiesMainActivity extends PinActivity implements MegaGlobalListenerInterface, MegaTransferListenerInterface, MegaRequestListenerInterface {
	
	TextView nameView;
	TextView contentTextView;
	RoundedImageView imageView;
	RelativeLayout contentLayout;
	TextView contentDetailedTextView;
	TextView infoEmail;
	TextView infoAdded;
	ImageView statusImageView;
	ImageButton eyeButton;
	TableLayout contentTable;
	ActionBar aB;
	
	String userEmail;
	
	MegaApiAndroid megaApi;
	
	ContactPropertiesFragment cpF;
	ContactFileListFragment cflF;
	
	MenuItem uploadButton;
	Stack<Long> parentHandleStack = new Stack<Long>();
	
	public UploadHereDialog uploadDialog;
	
	public static final int CONTACT_PROPERTIES = 1000;
	public static final int CONTACT_FILE_LIST = 1001;
	
	public static int REQUEST_CODE_SELECT_MOVE_FOLDER = 1001;
	public static int REQUEST_CODE_SELECT_COPY_FOLDER = 1002;
	public static final int REQUEST_CODE_SELECT_LOCAL_FOLDER = 1004;
	
	private static int EDIT_TEXT_ID = 2;
	
	long parentHandle = -1;
	
	DatabaseHandler dbH = null;
	MegaPreferences prefs = null;
	
	private int orderGetChildren = MegaApiJava.ORDER_DEFAULT_ASC;
	
	private AlertDialog renameDialog;
	ProgressDialog statusDialog;
	
	TransferList tL;
	long lastTimeOnTransferUpdate = -1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (megaApi == null){
			megaApi = ((MegaApplication) getApplication()).getMegaApi();
		}
		
		megaApi.addGlobalListener(this);
		megaApi.addTransferListener(this);
		
		aB = getSupportActionBar();
		aB.setHomeButtonEnabled(true);
		aB.setDisplayShowTitleEnabled(true);
		aB.setLogo(R.drawable.ic_action_navigation_accept);

		
		Display display = getWindowManager().getDefaultDisplay();
		DisplayMetrics outMetrics = new DisplayMetrics ();
	    display.getMetrics(outMetrics);
	    float density  = getResources().getDisplayMetrics().density;
		
	    float scaleW = Util.getScaleW(outMetrics, density);
	    float scaleH = Util.getScaleH(outMetrics, density);
		
		Bundle extras = getIntent().getExtras();
		if (extras != null){
			userEmail = extras.getString("name");
			aB.setTitle(userEmail);
			setContentView(R.layout.activity_main_contact_properties);
			
			int currentFragment = CONTACT_PROPERTIES;
			selectContactFragment(currentFragment);
		}
	}
	
	public void selectContactFragment(int currentFragment){
		switch(currentFragment){
			case CONTACT_PROPERTIES:{
				if (cpF == null){
					cpF = new ContactPropertiesFragment();
				}
				cpF.setUserEmail(userEmail);
				
				if (aB != null){
					aB.setTitle(userEmail);
				}
				
				getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_contact_properties, cpF, "cpF").commit();
				
				break;
			}
			case CONTACT_FILE_LIST:{
				if (cflF == null){
					cflF = new ContactFileListFragment();
				}
				cflF.setUserEmail(userEmail);
								
				getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_contact_properties, cflF, "cflF").commit();
				
				break;
			}
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
		    case android.R.id.home: {
				onBackPressed();
				return true;
			}
			case R.id.action_contact_file_list_upload: {
				uploadDialog = new UploadHereDialog();
				uploadDialog.show(getSupportFragmentManager(), "fragment_upload");
				return true;
			}
			default: {
				return super.onOptionsItemSelected(item);
			}
		}	    
	}
	
	public String getDescription(NodeList nodes){
		int numFolders = 0;
		int numFiles = 0;
		
		for (int i=0;i<nodes.size();i++){
			MegaNode c = nodes.get(i);
			if (c.isFolder()){
				numFolders++;
			}
			else{
				numFiles++;
			}
		}
		
		String info = "";
		if (numFolders > 0){
			info = numFolders +  " " + getResources().getQuantityString(R.plurals.general_num_folders, numFolders);
			if (numFiles > 0){
				info = info + ", " + numFiles + " " + getResources().getQuantityString(R.plurals.general_num_files, numFiles);
			}
		}
		else {
			if (numFiles == 0){
				info = numFiles +  " " + getResources().getQuantityString(R.plurals.general_num_folders, numFolders);
			}
			else{
				info = numFiles +  " " + getResources().getQuantityString(R.plurals.general_num_files, numFiles);
			}
		}
		
		return info;
	}
	
	public void onContentClick(String userEmail){
		if (userEmail.compareTo(this.userEmail) == 0){
			selectContactFragment(CONTACT_FILE_LIST);
		}
	}
	
	 @Override
    protected void onDestroy(){
    	log("onDestroy()");

    	super.onDestroy();    	    	
    	
		megaApi.removeGlobalListener(this);	
		megaApi.removeTransferListener(this);
    }
	 
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_contact_file_list, menu);
	
		uploadButton = menu.findItem(R.id.action_contact_file_list_upload);
		if (parentHandleStack.isEmpty()) {
			uploadButton.setVisible(false);
		}
	
		// MenuItem nullItem =
		// menu.findItem(R.id.action_contact_file_list_null);
		// nullItem.setEnabled(false);
	
		return super.onCreateOptionsMenu(menu);
	}
	
	public void setParentHandle(long parentHandle) {
		this.parentHandle = parentHandle;
	}
	
	public void onFileClick(ArrayList<Long> handleList) {
		long size = 0;
		long[] hashes = new long[handleList.size()];
		for (int i = 0; i < handleList.size(); i++) {
			hashes[i] = handleList.get(i);
			size += megaApi.getNodeByHandle(hashes[i]).getSize();
		}

		if (dbH == null) {
			dbH = DatabaseHandler.getDbHandler(getApplicationContext());
//			dbH = new DatabaseHandler(getApplicationContext());
		}

		boolean askMe = true;
		String downloadLocationDefaultPath = "";
		prefs = dbH.getPreferences();
		if (prefs != null) {
			if (prefs.getStorageAskAlways() != null) {
				if (!Boolean.parseBoolean(prefs.getStorageAskAlways())) {
					if (prefs.getStorageDownloadLocation() != null) {
						if (prefs.getStorageDownloadLocation().compareTo("") != 0) {
							askMe = false;
							downloadLocationDefaultPath = prefs.getStorageDownloadLocation();
						}
					}
				}
			}
		}

		if (askMe) {
			Intent intent = new Intent(Mode.PICK_FOLDER.getAction());
			intent.putExtra(FileStorageActivity.EXTRA_BUTTON_PREFIX,
					getString(R.string.context_download_to));
			intent.putExtra(FileStorageActivity.EXTRA_SIZE, size);
			intent.setClass(this, FileStorageActivity.class);
			intent.putExtra(FileStorageActivity.EXTRA_DOCUMENT_HASHES, hashes);
			startActivityForResult(intent, REQUEST_CODE_SELECT_LOCAL_FOLDER);
		} else {
			downloadTo(downloadLocationDefaultPath, null, size, hashes);
		}
	}
	
	public void downloadTo(String parentPath, String url, long size,
			long[] hashes) {
		double availableFreeSpace = Double.MAX_VALUE;
		try {
			StatFs stat = new StatFs(parentPath);
			availableFreeSpace = (double) stat.getAvailableBlocks()
					* (double) stat.getBlockSize();
		} catch (Exception ex) {
		}

		if (hashes == null) {
			if (url != null) {
				if (availableFreeSpace < size) {
					Util.showErrorAlertDialog(
							getString(R.string.error_not_enough_free_space),
							false, this);
					return;
				}

				Intent service = new Intent(this, DownloadService.class);
				service.putExtra(DownloadService.EXTRA_URL, url);
				service.putExtra(DownloadService.EXTRA_SIZE, size);
				service.putExtra(DownloadService.EXTRA_PATH, parentPath);
				service.putExtra(DownloadService.EXTRA_CONTACT_ACTIVITY, true);
				startService(service);
			}
		} else {
			if (hashes.length == 1) {
				MegaNode tempNode = megaApi.getNodeByHandle(hashes[0]);
				if ((tempNode != null)
						&& tempNode.getType() == MegaNode.TYPE_FILE) {
					log("ISFILE");
					String localPath = Util.getLocalFile(this,tempNode.getName(), tempNode.getSize(), parentPath);
					if (localPath != null) {
						try {
							Util.copyFile(new File(localPath), new File(parentPath, tempNode.getName()));
						} catch (Exception e) {
						}

						Intent viewIntent = new Intent(Intent.ACTION_VIEW);
						viewIntent.setDataAndType(Uri.fromFile(new File(localPath)),
								MimeType.typeForName(tempNode.getName()).getType());
						if (isIntentAvailable(this, viewIntent))
							startActivity(viewIntent);
						else {
							Intent intentShare = new Intent(Intent.ACTION_SEND);
							intentShare.setDataAndType(Uri.fromFile(new File(localPath)),
									MimeType.typeForName(tempNode.getName()).getType());
							if (isIntentAvailable(this, intentShare))
								startActivity(intentShare);
							String toastMessage = getString(R.string.already_downloaded) + ": " + localPath;
							Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
						}
						return;
					}
				}
			}

			for (long hash : hashes) {
				MegaNode node = megaApi.getNodeByHandle(hash);
				if (node != null) {
					Map<MegaNode, String> dlFiles = new HashMap<MegaNode, String>();
					if (node.getType() == MegaNode.TYPE_FOLDER) {
						getDlList(dlFiles, node, new File(parentPath, new String(node.getName())));
					} else {
						dlFiles.put(node, parentPath);
					}

					for (MegaNode document : dlFiles.keySet()) {

						String path = dlFiles.get(document);

						if (availableFreeSpace < document.getSize()) {
							Util.showErrorAlertDialog(getString(R.string.error_not_enough_free_space)	+ " (" + new String(document.getName()) + ")", false, this);
							continue;
						}

						Intent service = new Intent(this, DownloadService.class);
						service.putExtra(DownloadService.EXTRA_HASH, document.getHandle());
						service.putExtra(DownloadService.EXTRA_URL, url);
						service.putExtra(DownloadService.EXTRA_SIZE, document.getSize());
						service.putExtra(DownloadService.EXTRA_PATH, path);
						startService(service);
					}
				} else if (url != null) {
					if (availableFreeSpace < size) {
						Util.showErrorAlertDialog(getString(R.string.error_not_enough_free_space), false, this);
						continue;
					}

					Intent service = new Intent(this, DownloadService.class);
					service.putExtra(DownloadService.EXTRA_HASH, hash);
					service.putExtra(DownloadService.EXTRA_URL, url);
					service.putExtra(DownloadService.EXTRA_SIZE, size);
					service.putExtra(DownloadService.EXTRA_PATH, parentPath);
					startService(service);
				} else {
					log("node not found");
				}
			}
		}
	}
	
	/*
	 * Get list of all child files
	 */
	private void getDlList(Map<MegaNode, String> dlFiles, MegaNode parent,
			File folder) {

		if (megaApi.getRootNode() == null)
			return;

		folder.mkdir();
		NodeList nodeList = megaApi.getChildren(parent, orderGetChildren);
		for (int i = 0; i < nodeList.size(); i++) {
			MegaNode document = nodeList.get(i);
			if (document.getType() == MegaNode.TYPE_FOLDER) {
				File subfolder = new File(folder,
						new String(document.getName()));
				getDlList(dlFiles, document, subfolder);
			} else {
				dlFiles.put(document, folder.getAbsolutePath());
			}
		}
	}
	
	/*
	 * If there is an application that can manage the Intent, returns true.
	 * Otherwise, false.
	 */
	public static boolean isIntentAvailable(Context ctx, Intent intent) {

		final PackageManager mgr = ctx.getPackageManager();
		List<ResolveInfo> list = mgr.queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		return list.size() > 0;
	}
	
	public void moveToTrash(ArrayList<Long> handleList) {

		if (!Util.isOnline(this)) {
			Util.showErrorAlertDialog(getString(R.string.error_server_connection_problem), false, this);
			return;
		}

		if (isFinishing()) {
			return;
		}

		MegaNode rubbishNode = megaApi.getRubbishNode();

		for (int i = 0; i < handleList.size(); i++) {
			// Check if the node is not yet in the rubbish bin (if so, remove
			// it)
			MegaNode parent = megaApi.getNodeByHandle(handleList.get(i));
			while (megaApi.getParentNode(parent) != null) {
				parent = megaApi.getParentNode(parent);
			}

			if (parent.getHandle() != megaApi.getRubbishNode().getHandle()) {
				megaApi.moveNode(megaApi.getNodeByHandle(handleList.get(i)), rubbishNode, this);
			} 
		}

		ProgressDialog temp = null;
		try {
			temp = new ProgressDialog(this);
			temp.setMessage(getString(R.string.context_move_to_trash));
			temp.show();
		} catch (Exception e) {
			return;
		}
		statusDialog = temp;
	}
	
	public void showRenameDialog(final MegaNode document, String text) {
		
		final EditTextCursorWatcher input = new EditTextCursorWatcher(this);
		input.setId(EDIT_TEXT_ID);
		input.setSingleLine();
		input.setImeOptions(EditorInfo.IME_ACTION_DONE);

		input.setImeActionLabel(getString(R.string.context_rename), KeyEvent.KEYCODE_ENTER);
		input.setText(text);
		input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(final View v, boolean hasFocus) {
				if (hasFocus) {
					if (document.isFolder()) {
						input.setSelection(0, input.getText().length());
					} else {
						String[] s = document.getName().split("\\.");
						if (s != null) {
							int numParts = s.length;
							int lastSelectedPos = 0;
							if (numParts == 1) {
								input.setSelection(0, input.getText().length());
							} else if (numParts > 1) {
								for (int i = 0; i < (numParts - 1); i++) {
									lastSelectedPos += s[i].length();
									lastSelectedPos++;
								}
								lastSelectedPos--; // The last point should not
													// be selected)
								input.setSelection(0, lastSelectedPos);
							}
						}
						// showKeyboardDelayed(v);
					}
				}
			}
		});

		AlertDialog.Builder builder = Util.getCustomAlertBuilder(this, getString(R.string.context_rename) + " " + new String(document.getName()), null, input);
		builder.setPositiveButton(getString(R.string.context_rename), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String value = input.getText().toString().trim();
					if (value.length() == 0) {
						return;
					}
					rename(document, value);
				}
			});
		builder.setNegativeButton(getString(android.R.string.cancel), null);
		renameDialog = builder.create();
		renameDialog.show();

		input.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					renameDialog.dismiss();
					String value = v.getText().toString().trim();
					if (value.length() == 0) {
						return true;
					}
					rename(document, value);
					return true;
				}
				return false;
			}
		});
	}
	
	private void rename(MegaNode document, String newName) {
		if (newName.equals(document.getName())) {
			return;
		}

		if (!Util.isOnline(this)) {
			Util.showErrorAlertDialog(
					getString(R.string.error_server_connection_problem), false,
					this);
			return;
		}

		if (isFinishing()) {
			return;
		}

		ProgressDialog temp = null;
		try {
			temp = new ProgressDialog(this);
			temp.setMessage(getString(R.string.context_renaming));
			temp.show();
		} catch (Exception e) {
			return;
		}
		statusDialog = temp;

		log("renaming " + document.getName() + " to " + newName);

		megaApi.renameNode(document, newName, this);
	}
	
	public void showMove(ArrayList<Long> handleList){
		
		Intent intent = new Intent(this, FileExplorerActivity.class);
		intent.setAction(FileExplorerActivity.ACTION_PICK_MOVE_FOLDER);
		long[] longArray = new long[handleList.size()];
		for (int i=0; i<handleList.size(); i++){
			longArray[i] = handleList.get(i);
		}
		intent.putExtra("MOVE_FROM", longArray);
		startActivityForResult(intent, REQUEST_CODE_SELECT_MOVE_FOLDER);
	}
	
	public void showCopy(ArrayList<Long> handleList) {
		
		Intent intent = new Intent(this, FileExplorerActivity.class);
		intent.setAction(FileExplorerActivity.ACTION_PICK_COPY_FOLDER);
		long[] longArray = new long[handleList.size()];
		for (int i = 0; i < handleList.size(); i++) {
			longArray[i] = handleList.get(i);
		}
		intent.putExtra("COPY_FROM", longArray);
		startActivityForResult(intent, REQUEST_CODE_SELECT_COPY_FOLDER);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (intent == null) {
			return;
		}
		
		if (requestCode == REQUEST_CODE_SELECT_LOCAL_FOLDER	&& resultCode == RESULT_OK) {
			log("local folder selected");
			String parentPath = intent
					.getStringExtra(FileStorageActivity.EXTRA_PATH);
			String url = intent.getStringExtra(FileStorageActivity.EXTRA_URL);
			long size = intent.getLongExtra(FileStorageActivity.EXTRA_SIZE, 0);
			long[] hashes = intent.getLongArrayExtra(FileStorageActivity.EXTRA_DOCUMENT_HASHES);
			log("URL: " + url + "___SIZE: " + size);

			downloadTo(parentPath, url, size, hashes);
			Util.showToast(this, R.string.download_began);
		} 
		else if (requestCode == REQUEST_CODE_SELECT_COPY_FOLDER	&& resultCode == RESULT_OK) {
			if (!Util.isOnline(this)) {
				Util.showErrorAlertDialog(
						getString(R.string.error_server_connection_problem),
						false, this);
				return;
			}

			ProgressDialog temp = null;
			try {
				temp = new ProgressDialog(this);
				temp.setMessage(getString(R.string.context_copying));
				temp.show();
			} catch (Exception e) {
				return;
			}
			statusDialog = temp;

			final long[] copyHandles = intent.getLongArrayExtra("COPY_HANDLES");
			final long toHandle = intent.getLongExtra("COPY_TO", 0);
			final int totalCopy = copyHandles.length;

			MegaNode parent = megaApi.getNodeByHandle(toHandle);
			for (int i = 0; i < copyHandles.length; i++) {
				log("NODO A COPIAR: " + megaApi.getNodeByHandle(copyHandles[i]).getName());
				log("DONDE: " + parent.getName());
				log("NODOS: " + copyHandles[i] + "_" + parent.getHandle());
				megaApi.copyNode(megaApi.getNodeByHandle(copyHandles[i]), parent, this);
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		if (cflF != null){
			if (cflF.isVisible()){
				if (cflF.onBackPressed() == 0){
					selectContactFragment(CONTACT_PROPERTIES);
					return;
				}
			}
		}
		
		if (cpF != null){
			if (cpF.isVisible()){
				super.onBackPressed();
				return;
			}
		}
	}

	@Override
	public void onTransferStart(MegaApiJava api, MegaTransfer transfer) {
		log("onTransferStart");
		
		HashMap<Long, MegaTransfer> mTHash = new HashMap<Long, MegaTransfer>();

		tL = megaApi.getTransfers();

		if (cflF != null){
			for(int i=0; i<tL.size(); i++){
				
				MegaTransfer tempT = tL.get(i).copy();
				if (tempT.getType() == MegaTransfer.TYPE_DOWNLOAD){
					long handleT = tempT.getNodeHandle();
					MegaNode nodeT = megaApi.getNodeByHandle(handleT);
					MegaNode parentT = megaApi.getParentNode(nodeT);
					
					if (parentT != null){
						if(parentT.getHandle() == this.parentHandle){	
							mTHash.put(handleT,tempT);						
						}
					}
				}
			}
			
			cflF.setTransfers(mTHash);
		}
		
		log("onTransferStart: " + transfer.getFileName() + " - " + transfer.getTag());
	}

	@Override
	public void onTransferFinish(MegaApiJava api, MegaTransfer transfer,
			MegaError e) {
		log("onTransferFinish");
		
		HashMap<Long, MegaTransfer> mTHash = new HashMap<Long, MegaTransfer>();

		tL = megaApi.getTransfers();

		if (cflF != null){
			for(int i=0; i<tL.size(); i++){
				
				MegaTransfer tempT = tL.get(i).copy();
				if (tempT.getType() == MegaTransfer.TYPE_DOWNLOAD){
					long handleT = tempT.getNodeHandle();
					MegaNode nodeT = megaApi.getNodeByHandle(handleT);
					MegaNode parentT = megaApi.getParentNode(nodeT);
					
					if (parentT != null){
						if(parentT.getHandle() == this.parentHandle){	
							mTHash.put(handleT,tempT);						
						}
					}
				}
			}
			
			cflF.setTransfers(mTHash);
		}
		
		log("onTransferFinish: " + transfer.getFileName() + " - " + transfer.getTag());
	}

	@Override
	public void onTransferUpdate(MegaApiJava api, MegaTransfer transfer) {
		log("onTransferUpdate: " + transfer.getFileName() + " - " + transfer.getTag());

		if (cflF != null){
			if (cflF.isVisible()){
				if (transfer.getType() == MegaTransfer.TYPE_DOWNLOAD){
					Time now = new Time();
					now.setToNow();
					long nowMillis = now.toMillis(false);
					if (lastTimeOnTransferUpdate < 0){
						lastTimeOnTransferUpdate = now.toMillis(false);
						cflF.setCurrentTransfer(transfer);
					}
					else if ((nowMillis - lastTimeOnTransferUpdate) > Util.ONTRANSFERUPDATE_REFRESH_MILLIS){
						lastTimeOnTransferUpdate = nowMillis;
						cflF.setCurrentTransfer(transfer);
					}			
				}		
			}
		}
	}

	@Override
	public void onTransferTemporaryError(MegaApiJava api,
			MegaTransfer transfer, MegaError e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onTransferData(MegaApiJava api, MegaTransfer transfer,
			byte[] buffer) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onUsersUpdate(MegaApiJava api) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onNodesUpdate(MegaApiJava api) {
		if (cflF != null){
			if (cflF.isVisible()){
				cflF.setNodes(parentHandle);
			}
		}
	}

	@Override
	public void onReloadNeeded(MegaApiJava api) {
		// TODO Auto-generated method stub
		
	}
	
	public static void log(String log) {
		Util.log("ContactPropertiesMainActivity", log);
	}

	@Override
	public void onRequestStart(MegaApiJava api, MegaRequest request) {
		if (request.getType() == MegaRequest.TYPE_MOVE) {
			log("move request start");
		} 
		else if (request.getType() == MegaRequest.TYPE_REMOVE) {
			log("remove request start");
		} 
		else if (request.getType() == MegaRequest.TYPE_EXPORT) {
			log("export request start");
		} 
		else if (request.getType() == MegaRequest.TYPE_RENAME) {
			log("rename request start");
		} 
		else if (request.getType() == MegaRequest.TYPE_COPY) {
			log("copy request start");
		}

	}

	@Override
	public void onRequestUpdate(MegaApiJava api, MegaRequest request) {
		log("onRequestUpdate");		
	}

	@Override
	public void onRequestFinish(MegaApiJava api, MegaRequest request,
			MegaError e) {
		log("onRequestFinish");
		
		if (request.getType() == MegaRequest.TYPE_RENAME){
			try { 
				statusDialog.dismiss();	
			} 
			catch (Exception ex) {}
			
			if (e.getErrorCode() == MegaError.API_OK){
				Toast.makeText(this, "Correctly renamed", Toast.LENGTH_SHORT).show();
			}
			else{
				Toast.makeText(this, "The file has not been renamed", Toast.LENGTH_LONG).show();
			}
			log("rename nodes request finished");			
		}
		else if (request.getType() == MegaRequest.TYPE_COPY) {
			try {
				statusDialog.dismiss();
			} catch (Exception ex) {
			}

			if (e.getErrorCode() == MegaError.API_OK) {
				Toast.makeText(this, "Correctly copied", Toast.LENGTH_SHORT).show();				
			} else {
				Toast.makeText(this, "The file has not been copied", Toast.LENGTH_LONG).show();
			}
			log("copy nodes request finished");
		}
		else if (request.getType() == MegaRequest.TYPE_MOVE){
			try { 
				statusDialog.dismiss();	
			} 
			catch (Exception ex) {}
			
			if (e.getErrorCode() == MegaError.API_OK){
				Toast.makeText(this, "Correctly moved to Rubbish bin", Toast.LENGTH_SHORT).show();
			}
			else{
				Toast.makeText(this, "The file has not been removed", Toast.LENGTH_LONG).show();
			}
			log("move to rubbish request finished");
		}
	}

	@Override
	public void onRequestTemporaryError(MegaApiJava api, MegaRequest request,
			MegaError e) {
		log("onRequestTemporaryError");
	}


}

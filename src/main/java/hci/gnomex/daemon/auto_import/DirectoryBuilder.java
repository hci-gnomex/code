package hci.gnomex.daemon.auto_import;

import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.utility.*;
import org.hibernate.Session;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.persistence.criteria.CriteriaBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DirectoryBuilder {

	private String inFileName;
	private String root;
	private String currentDownloadLocation;
	private boolean skip = false;
	private String flaggedIDFileName;
	private String mode;
	private String accountForFilesMoved;
	private Set<String> fileTypeCategorySet;



	private static final String RNAseq = "RNAseq";
	private static final String WHOLE_EXOME= "Whole_Exome";
	private static final String FASTQ="FASTq";
	private static final String DNA_ALIAS="DNA";
	private static final String RNA_ALIAS="RNA";
	private static final String FASTQ_ALIAS="Fastq";
	private static final Integer AVATAR_GROUP_ID = 11;
	private static final Integer FOUNDATION_GROUP_ID = 14;
	private static final int TEST_TYPE_PROPERTY_ID = 21;




	public DirectoryBuilder(String[] args) {

		for (int i = 0; i < args.length; i++) {
			args[i] =  args[i].toLowerCase();

			if(args[i].equals("-accountfilesmoved"))
				accountForFilesMoved = args[++i];
			if (args[i].equals("-file")) {
				this.inFileName = args[++i];
			} else if (args[i].equals("-root")) {
				this.root = args[++i];
			} else if (args[i].equals("-downloadpath")) {
				this.currentDownloadLocation = args[++i];
			} else if(args[i].equals("-flaggedfile")){
				flaggedIDFileName = args[++i];
			}else if(args[i].equals("-skipfirst")){
				this.skip = true;
			}else if(args[i].equals("-mode")) {
				this.mode = args[++i];
			}
			else if (args[i].equals("-help")) {
				//printUsage();
				System.exit(0);
			}
		}
	}


	public boolean isAccountedMode(){
		return (this.accountForFilesMoved != null);
	}

	public void printAccoutedForFiles(Map<String, List<String>> missingMap) {
		for(String key : missingMap.keySet() ) {
			List<String> missingList =  missingMap.get(key);
			System.out.print( key + ": ");
			for(int i = 0; i <  missingList.size(); i++ ) {
				String comma =  ", ";
				if(i == missingList.size() - 1) {
					comma = "";
				}

				System.out.print(missingList.get(i) + comma);
			}
			System.out.println();

		}
	}

	public void makeAccountingForFiles(){
		this.fileTypeCategorySet = new HashSet<String>();
		Map<String,List<String>> missingMap = new TreeMap<String,List<String>>();
		fileTypeCategorySet = new HashSet<>(Arrays.asList(".pdf", ".xml",".deident.xml", ".bam.bai",".bam",".bam.bai.md5",".bam.md5" ));



		File root = new File(this.accountForFilesMoved);
		if(root.exists() && root.isDirectory()){
			Map<String, Set<String>> fileMap = this.findAllFiles(root);
			for(String key : fileMap.keySet()) {

				Set<String> fileTypes = fileMap.get(key);
				for(String type : fileTypeCategorySet ) {
					if(!fileTypes.contains(type)) {
						if(missingMap.get(key) != null ) {
							missingMap.get(key).add(type);
						}else {
							missingMap.put(key, new ArrayList<String>(Arrays.asList(type)));
						}
					}
				}

			}

			printAccoutedForFiles(missingMap);



		}else{
			System.out.println("This path is invalid");
			System.exit(1);
		}
	}


	private Map<String, Set<String>> findAllFiles(File root){
		Map<String, Set<String>> fileMap = new TreeMap<String, Set<String> >();
		findAllFilesRecursively(root, fileMap);
		return fileMap;
	}

	private void findAllFilesRecursively(File file, Map<String, Set<String>> fileMap){

		if(!file.isDirectory()){
			String name  = file.getName();
			//int startIndx = name.indexOf(".");
			//String extension =  name.substring(startIndx + 1 , name.length());


			String regex = "^([a-zA-Z0-9]+)_?[a-zA-Z]*(\\..+)$";
			Pattern r = Pattern.compile(regex);

			Matcher m = r.matcher(name);
			String id ="";
			String extension ="";

			if(m.matches()) {
				id = m.group(1);
				extension= m.group(2);
			}else{
				System.out.println("didn't match " + name);
			}

			if(fileMap.get(id) != null) {
				fileMap.get(id).add(extension);
			}else{
				HashSet<String> extensionList = new HashSet<String>();
				extensionList.add(extension);
				fileMap.put(id, extensionList);
			}

		}else{
			File[] fileList =  file.listFiles();
			for(File f : fileList){
				findAllFilesRecursively(f,fileMap);
			}
		}


	}




	public List<String> readPathInfo(String fileName) throws IOException{
		BufferedReader bf = null;
		List<String> dataFromFileList = new ArrayList();

		try {
			bf = new BufferedReader(new FileReader(fileName));
			String line = "";
			int count = 0;
			while((line= bf.readLine()) != null) {

				if(count % 7 == 2 && count != 0) {
					dataFromFileList.add(line);
				}
				count++;
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		finally {
			bf.close();
		}
		return dataFromFileList;
	}


	public List<String> readFile(String fileName) throws IOException{
		BufferedReader bf = null;
		List<String> dataFromFileList = new ArrayList();

		try {
			bf = new BufferedReader(new FileReader(fileName));
			String line = "";
			int count = 0;
			while((line= bf.readLine()) != null) {
				if(!skip || count > 0) {
					dataFromFileList.add(line);
				}
				count++;
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		finally {
			bf.close();
		}
		return dataFromFileList;
	}

	public List<String> preparePath() {
		List<String> paths = new ArrayList<String>();
		Set<String> pathToCreate = new HashSet<String>();
		List<String> filesWithPaths = new ArrayList<String>();
		List<String> filteredFiles = new ArrayList<String>();
		List<String> flaggedIDList = readSampleIDs(flaggedIDFileName);



		try {

			paths = this.readFile(this.inFileName);
			boolean test = new File(this.currentDownloadLocation + "/Flagged").mkdir();

			//readPathInfo(this.pathCreatorInfo);

			if(mode.equals("avatar")) {
				for(String p: paths) {
					StringBuilder strBuild = new StringBuilder("/");

					String[] pathChunks = p.split("/");
					boolean foundPath = false;

					for(int i = 0; i < pathChunks.length; i++) {
						if (pathChunks[i].equals(DirectoryBuilder.WHOLE_EXOME)) {
							strBuild.append(DirectoryBuilder.DNA_ALIAS);
							strBuild.append("/");

							if(pathChunks[i+1].equals(DirectoryBuilder.FASTQ)) {
								strBuild.append(DirectoryBuilder.FASTQ_ALIAS);
							}else {
								strBuild.append(pathChunks[i+1]);
							}
							strBuild.append("/");
							foundPath = true;
							break;
						}
						else if(pathChunks[i].equals(DirectoryBuilder.RNAseq)){
							strBuild.append(DirectoryBuilder.RNA_ALIAS);
							strBuild.append("/");
							if(pathChunks[i+1].equals(DirectoryBuilder.FASTQ)) {
								strBuild.append(DirectoryBuilder.FASTQ_ALIAS);

							}else {
								strBuild.append(pathChunks[i+1]);
							}
							strBuild.append("/");
							foundPath = true;
							break;
						}
					}

					if(foundPath) {

						if(!filterOutFlaggedIDs(pathChunks[pathChunks.length - 1], flaggedIDList)) {
							pathToCreate.add(this.root + strBuild.toString());
							filesWithPaths.add(this.root + strBuild.toString() + pathChunks[pathChunks.length - 1]);
						}else {
							filteredFiles.add(this.currentDownloadLocation + "Flagged/"+ pathChunks[pathChunks.length - 1] );
						}

					}


				}

				for(String p: pathToCreate ) {
					boolean made = new File(p).mkdirs();
				}

			}else if(mode.equals("foundation")) {
				prepareFoundationPath(flaggedIDList,filteredFiles, paths,filesWithPaths );
			}






		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		this.moveTheFiles(filteredFiles); // These files we want to move into the flagged folder
		return filesWithPaths; // These are the files we want to move to where data lives for gnomex

		//boolean made = new File("C:\\Users\\u0566434\\Desktop\\ORIEN\\tEST13\\TESTAGAIN").mkdir();


	}



	private void prepareFoundationPath(List<String> flaggedFiles,List<String> filteredFiles, List<String> paths, List<String> filesWithPaths){

		for(String p: paths) {
			StringBuilder strBuild = new StringBuilder("/");

			String[] pathChunks = p.split("/"); // path doesn't matter just need fileName
			String fileName = pathChunks[pathChunks.length - 1];
			String[] extChunks = fileName.split("\\.");
			int length = extChunks.length;

			if(extChunks.length > 1 ) {
				if(filterOutFlaggedIDs( fileName , flaggedFiles)) {
					strBuild.append(currentDownloadLocation);
					strBuild.append("/Flagged/");
					strBuild.append(fileName);
					filteredFiles.add(strBuild.toString());
					continue;
				}
				if(extChunks[1].equals("bam")) {
					strBuild.append(this.root);
					strBuild.append("Bams/");

					if(extChunks[length - 1].equals("md5")){
						strBuild.append("checksums/");
					}
					strBuild.append(fileName);
					filesWithPaths.add(strBuild.toString());

				}else if(extChunks[1].equals("vcf")) {
					strBuild.append(this.root);
					strBuild.append("VCF/");
					strBuild.append(fileName);
					filesWithPaths.add(strBuild.toString());
				}
				else {
					strBuild.append(this.root);
					strBuild.append("Reports/");
					strBuild.append(fileName);
					filesWithPaths.add(strBuild.toString());

				}

			}

		}



	}




	private boolean filterOutFlaggedIDs(String idToCheck, List<String> flaggedIDList) {
		boolean filterID = false;

		for(int i=0; i < flaggedIDList.size(); i++) {
			String flaggedID = flaggedIDList.get(i);
			int index = idToCheck.indexOf(flaggedID);
			if(index > -1) {
				int end = index + flaggedID.length();
				if(idToCheck.charAt(end) == '.' || idToCheck.charAt(end) ==  '_') {
					filterID = true;
					break;
				}

			}

		}


		return filterID ;
	}


	public String excludedString(String matchStr) {
		StringBuilder pathBuilder =  new StringBuilder();

		//String regex = "^(?:(\\w+) )?((?:[\\w \\.]+/)*[\\w \\.]+)$";
		String regex = "(\\/\\w+_\\d+_\\d+)";
		Pattern r = Pattern.compile(regex);
		StringBuffer strBuild = new StringBuffer();

		// Now create matcher object.
		Matcher m = r.matcher(matchStr);
		if (m.find( )) {
			System.out.println("Found value: " + m.group(0) );
			m.appendReplacement(strBuild, "");

		}
		m.appendTail(strBuild);
		String excludedString = strBuild.toString();
		String[] pathChunks = excludedString.split("/");
		int len = pathChunks.length;
		for(int i= 0; i < len; i++) {
			if(i == 0) {
				continue;
			}
			if(i == len - 1) {
				continue;
			}
			pathBuilder.append(pathChunks[i]);
			pathBuilder.append("/");

		}


		return pathBuilder.toString();

	}

	public void moveTheFiles(List<String> files) {
		StringBuilder strBuild = new StringBuilder();
		List<String> commands = new ArrayList<String>();

		strBuild.append("cd " );
		strBuild.append(this.currentDownloadLocation);
		commands.add(strBuild.toString());
		strBuild = new StringBuilder();


		for(String file: files) {
			String[] pWithf = file.split("/");

			strBuild.append("mv -vn");
			strBuild.append(" *");
			strBuild.append(pWithf[pWithf.length- 1]);
			strBuild.append(" ");
			strBuild.append(file);
			System.out.println(strBuild.toString());
			commands.add(strBuild.toString());
			strBuild = new StringBuilder();


		}

		this.executeCommands(commands);


	}



	private void executeCommands(List<String> commands) {

		File tempScript = null;

		try {
			System.out.println("started executing command");
			tempScript = createTempScript(commands);
			ProcessBuilder pb = new ProcessBuilder("bash", tempScript.toString());
			pb.inheritIO();
			Process process;
			process = pb.start();
			process.waitFor();
			System.out.println("finished executing command");
		}

		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		finally

		{
			tempScript.delete();
		}
	}


	private File createTempScript(List<String> commands) throws IOException {
		File tempScript = File.createTempFile("script", null);

		Writer streamWriter = new OutputStreamWriter(new FileOutputStream(tempScript));
		PrintWriter printWriter = new PrintWriter(streamWriter);


		for(int i =0; i < commands.size(); i++) {
			printWriter.println(commands.get(i));
		}

		printWriter.close();

		return tempScript;
	}

	private  List<String> readSampleIDs(String fileName){
		BufferedReader bf = null;
		List<String> idList = new ArrayList<String>();

		try {
			bf = new BufferedReader(new FileReader(new File(fileName)));
			String line = "";
			while((line = bf.readLine()) != null) {
				idList.add(line);
			}


		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}finally {
			try {
				bf.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return idList;

	}



	public void reportWorkSummary() {
		System.out.println("[FileMover:DirectoryBuilder->reportWorkSummary] MAKING FINAL REPORT");

		String path = XMLParser.getPathWithoutName(this.inFileName);
		List<String> sampleIDList = new ArrayList<String>();
		StringBuilder strBuild = new StringBuilder();
		String from = "DoNotReply@hci.utah.edu";
		String to = "erik.rasmussen@hci.utah.edu, dalton.wilson@hci.utah.edu, david.nix@hci.utah.edu, qing.li@hci.utah.edu, aaron.atkinson@hci.utah.edu";
		String subject = "GNomEx Importer Automated Email - Patient ID Report";
		Query q =  new Query(path+"gnomex-creds.properties");
		List<String> reportIDList = new ArrayList<String>();


		if(mode.equals("avatar")){
			sampleIDList =  readSampleIDs(path +"importedSLList.out");
			reportIDList =  q.getImportedIDReport(sampleIDList,DirectoryBuilder.AVATAR_GROUP_ID);

		}else{
			sampleIDList = readSampleIDs(path + "importedTRFList.out");
			reportIDList =  q.getImportedIDReport(sampleIDList, DirectoryBuilder.FOUNDATION_GROUP_ID);
		}

		//Map<String, HashMap<String,Long>> personMap = q.countPropertyByPerson(TEST_TYPE_PROPERTY_ID);


		q.closeConnection();

		strBuild.append("The following records have been successfully been imported into Translational GNomEx\n\n");
		strBuild.append("Exp ID\tAnalysis ID\tSample Name\tPerson ID\n");
		for(String id : reportIDList){
			strBuild.append(id);
		}

		if(reportIDList.size() > 0 ){
			try{
				sendImportedIDReport(from,to,subject,strBuild.toString(),"");
			}
			catch(Exception e){
				e.printStackTrace();
				System.exit(1);
			}

		}




	}
	public static void sendImportedIDReport(String from,String to, String subject,String body, String testEmail) {
			//PropertyDictionaryHelper ph = PropertyDictionaryHelper.getInstance(sess);
			//String gnomexSupportEmail = ph.getProperty(PropertyDictionary.GNOMEX_SUPPORT_EMAIL);

		try {
			Properties mailProps = new BatchMailer("").getMailProperties();
			System.out.println("The mailProps have been saved");
			System.out.println(mailProps.toString());

			boolean sendTestEmail = false;
			if (testEmail != null) {
				if (testEmail.length() > 0) {
					sendTestEmail = true;
				}
			}

			MailUtilHelper helper = new MailUtilHelper(
					mailProps,
					to,
					null,
					null,
					from,
					subject,
					body,
					null,
					false,
					sendTestEmail,
					testEmail
			);

			MailUtil.validateAndSendEmail(helper);
		} catch (NamingException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}catch(Exception e) {
			e.printStackTrace();
		}
		System.out.println("Email has been sent!");


	}

}

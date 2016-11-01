package hci.gnomex.useq.data;

import hci.gnomex.useq.SliceInfo;
import hci.gnomex.useq.USeqUtilities;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**Container for a sorted RegionScore[].
 * @author david.nix@hci.utah.edu*/
public class RegionScoreData extends USeqData{

	//fields
	private RegionScore[] sortedRegionScores;

	//constructors
	public RegionScoreData(){}

	/**Note, be sure to sort the RegionScore[].*/
	public RegionScoreData(RegionScore[] sortedRegionScores, SliceInfo sliceInfo){
		this.sortedRegionScores = sortedRegionScores;
		this.sliceInfo = sliceInfo;
	}
	public RegionScoreData(File binaryFile) throws IOException{
		sliceInfo = new SliceInfo(binaryFile.getName());
		read (binaryFile);
	}
	public RegionScoreData(DataInputStream dis, SliceInfo sliceInfo) {
		this.sliceInfo = sliceInfo;
		read (dis);
	}

	//methods
	/**Updates the SliceInfo setting just the FirstStartPosition, LastStartPosition, and NumberRecords.*/
	public static void updateSliceInfo (RegionScore[] sortedRegionScores, SliceInfo sliceInfo){
		sliceInfo.setFirstStartPosition(sortedRegionScores[0].getStart());
		sliceInfo.setLastStartPosition(sortedRegionScores[sortedRegionScores.length-1].start);
		sliceInfo.setNumberRecords(sortedRegionScores.length);
	}
	
	/**Returns the bp of the last end position in the array.*/
	public int fetchLastBase(){
		int lastBase = -1;
		for (RegionScore r : sortedRegionScores){
			int end = r.getStop();
			if (end > lastBase) lastBase = end;
		}
		return lastBase;
	}

	/**Writes six column xxx.bed formatted lines to the PrintWriter*/
	public void writeBed (PrintWriter out, boolean fixScore){
		String chrom = sliceInfo.getChromosome();
		String strand = sliceInfo.getStrand();
		for (int i=0; i< sortedRegionScores.length; i++){
			//chrom start stop name score strand
			if (fixScore){
				int score = USeqUtilities.fixBedScore(sortedRegionScores[i].score);
				out.println(chrom+"\t"+sortedRegionScores[i].start+"\t"+sortedRegionScores[i].stop+"\t"+".\t"+ score +"\t"+strand);
			}
			else out.println(chrom+"\t"+sortedRegionScores[i].start+"\t"+sortedRegionScores[i].stop+"\t"+".\t"+ sortedRegionScores[i].score +"\t"+strand);
		}
	}
	
	/**Writes native format to the PrintWriter*/
	public void writeNative (PrintWriter out){
		String chrom = sliceInfo.getChromosome();
		String strand = sliceInfo.getStrand();
		if (strand.equals(".")){
			out.println("#Chr\tStart\tStop\tScore");
			for (int i=0; i< sortedRegionScores.length; i++) out.println(chrom+"\t"+sortedRegionScores[i].start+"\t"+sortedRegionScores[i].stop+"\t"+sortedRegionScores[i].score);
		}
		else {
			out.println("#Chr\tStart\tStop\tScore\tStrand");
			for (int i=0; i< sortedRegionScores.length; i++) out.println(chrom+"\t"+sortedRegionScores[i].start+"\t"+sortedRegionScores[i].stop+"\t"+sortedRegionScores[i].score+"\t"+strand);
		}
	}
	
	/**Writes the RegionScore[] to a binary file.  Each region's start/stop is converted to a running offset/length which are written as either ints or shorts.
	 * @param saveDirectory, the binary file will be written using the chromStrandStartBP-StopBP.extension notation to this directory
	 * @param attemptToSaveAsShort, scans to see if the offsets and region lengths exceed 65536 bp, a bit slower to write but potentially a considerable size reduction, set to false for max speed
	 * @return the binaryFile written to the saveDirectory
	 * */
	public File write (File saveDirectory, boolean attemptToSaveAsShort) {
		//check to see if this can be saved using shorts instead of ints?
		boolean useShortBeginning = false;
		boolean useShortLength = false;
		if (attemptToSaveAsShort){			
			int bp = sortedRegionScores[0].start;
			useShortBeginning = true;
			for (int i=1; i< sortedRegionScores.length; i++){
				int currentStart = sortedRegionScores[i].start;
				int diff = currentStart - bp;
				if (diff > 65536) {
					useShortBeginning = false;
					break;
				}
				bp = currentStart;
			}
			//check to short length
			useShortLength = true;
			for (int i=0; i< sortedRegionScores.length; i++){
				int diff = sortedRegionScores[i].stop - sortedRegionScores[i].start;
				if (diff > 65536) {
					useShortLength = false;
					break;
				}
			}
		}

		//make and put file type/extension in header
		String fileType;
		if (useShortBeginning) fileType = USeqUtilities.SHORT;
		else fileType = USeqUtilities.INT;
		if (useShortLength) fileType = fileType+ USeqUtilities.SHORT;
		else fileType = fileType+ USeqUtilities.INT;
		fileType = fileType+ USeqUtilities.FLOAT;
		sliceInfo.setBinaryType(fileType);
		binaryFile = new File(saveDirectory, sliceInfo.getSliceName());

		FileOutputStream workingFOS = null;
		DataOutputStream workingDOS = null;
		try {
			//make IO
			workingFOS = new FileOutputStream(binaryFile);
			workingDOS = new DataOutputStream( new BufferedOutputStream (workingFOS));

			//write String header, currently this isn't used
			workingDOS.writeUTF(header);

			//write first position, always an int
			workingDOS.writeInt(sortedRegionScores[0].start);

			//write short position?
			int bp = sortedRegionScores[0].start;
			if (useShortBeginning) {			
				//also short length?
				//no
				if (useShortLength == false){
					//write first record's length
					workingDOS.writeInt(sortedRegionScores[0].stop- sortedRegionScores[0].start);
					workingDOS.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						//subtract 32768 to extend range of short (-32768 to 32768)
						int diff = currentStart - bp - 32768;
						workingDOS.writeShort((short)(diff));
						workingDOS.writeInt(sortedRegionScores[i].stop- sortedRegionScores[i].start);
						workingDOS.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
				//yes short length
				else {
					//write first record's length, subtracting 32768 to extent the range of the signed short
					workingDOS.writeShort((short)(sortedRegionScores[0].stop- sortedRegionScores[0].start - 32768));
					workingDOS.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						//subtract 32768 to extend range of short (-32768 to 32768)
						int diff = currentStart - bp - 32768;
						workingDOS.writeShort((short)(diff));
						workingDOS.writeShort((short)(sortedRegionScores[i].stop- sortedRegionScores[i].start - 32768));
						workingDOS.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
			}

			//no, write int for position
			else {
				//short length? no
				if (useShortLength == false){
					//write first record's length
					workingDOS.writeInt(sortedRegionScores[0].stop- sortedRegionScores[0].start);
					workingDOS.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						int diff = currentStart - bp;
						workingDOS.writeInt(diff);
						workingDOS.writeInt(sortedRegionScores[i].stop- sortedRegionScores[i].start);
						workingDOS.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
				//yes
				else {
					//write first record's length
					workingDOS.writeShort((short)(sortedRegionScores[0].stop- sortedRegionScores[0].start - 32768));
					workingDOS.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						int diff = currentStart - bp;
						workingDOS.writeInt(diff);
						workingDOS.writeShort((short)(sortedRegionScores[i].stop- sortedRegionScores[i].start - 32768));
						workingDOS.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
			}
		} catch (Exception e) {

			binaryFile = null;
		} finally {
			USeqUtilities.safeClose(workingDOS);
			USeqUtilities.safeClose(workingFOS);
		}
		return binaryFile;
	}
	
	/**Assumes all are of the same chromosome and strand!*/
	public static RegionScoreData merge (ArrayList<RegionScoreData> pdAL){
		//convert to arrays and sort
		RegionScoreData[] pdArray = new RegionScoreData[pdAL.size()];
		pdAL.toArray(pdArray);
		Arrays.sort(pdArray);
		//fetch total size of RegionScore[]
		int num = 0;
		for (int i=0; i< pdArray.length; i++) num += pdArray[i].sortedRegionScores.length;
		//concatinate
		RegionScore[] concatinate = new RegionScore[num];
		int index = 0;
		for (int i=0; i< pdArray.length; i++){
			RegionScore[] slice = pdArray[i].sortedRegionScores;
			System.arraycopy(slice, 0, concatinate, index, slice.length);
			index += slice.length;
		}
		//get and modify header
		SliceInfo sliceInfo = pdArray[0].sliceInfo;
		RegionScoreData.updateSliceInfo(concatinate, sliceInfo);
		//return new RegionScoreData
		return new RegionScoreData(concatinate, sliceInfo);
	}
	
	public static RegionScoreData mergeUSeqData(ArrayList<USeqData> useqDataAL) {
		int num = useqDataAL.size();
		//convert ArrayList
		ArrayList<RegionScoreData> a = new ArrayList<RegionScoreData>(num);
		for (int i=0; i< num; i++) a.add((RegionScoreData) useqDataAL.get(i));
		return merge (a);
	}

	/**Writes the Region[] to a ZipOutputStream.
	 * @param	attemptToSaveAsShort	if true, scans to see if the offsets exceed 65536 bp, a bit slower to write but potentially a considerable size reduction, set to false for max speed
	 * */
	public void write (ZipOutputStream out, DataOutputStream dos, boolean attemptToSaveAsShort) {
		//check to see if this can be saved using shorts instead of ints?
		boolean useShortBeginning = false;
		boolean useShortLength = false;
		if (attemptToSaveAsShort){			
			int bp = sortedRegionScores[0].start;
			useShortBeginning = true;
			for (int i=1; i< sortedRegionScores.length; i++){
				int currentStart = sortedRegionScores[i].start;
				int diff = currentStart - bp;
				if (diff > 65536) {
					useShortBeginning = false;
					break;
				}
				bp = currentStart;
			}
			//check to short length
			useShortLength = true;
			for (int i=0; i< sortedRegionScores.length; i++){
				int diff = sortedRegionScores[i].stop - sortedRegionScores[i].start;
				if (diff > 65536) {
					useShortLength = false;
					break;
				}
			}
		}

		//make and put file type/extension in header
		String fileType;
		if (useShortBeginning) fileType = USeqUtilities.SHORT;
		else fileType = USeqUtilities.INT;
		if (useShortLength) fileType = fileType+ USeqUtilities.SHORT;
		else fileType = fileType+ USeqUtilities.INT;
		fileType = fileType+ USeqUtilities.FLOAT;
		sliceInfo.setBinaryType(fileType);
		binaryFile = null;

		try {

			//make new ZipEntry
			out.putNextEntry(new ZipEntry(sliceInfo.getSliceName()));

			//write String header, currently this isn't used
			dos.writeUTF(header);

			//write first position, always an int
			dos.writeInt(sortedRegionScores[0].start);

			//write short position?
			int bp = sortedRegionScores[0].start;
			if (useShortBeginning) {			
				//also short length?
				//no
				if (useShortLength == false){
					//write first record's length
					dos.writeInt(sortedRegionScores[0].stop- sortedRegionScores[0].start);
					dos.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						//subtract 32768 to extend range of short (-32768 to 32768)
						int diff = currentStart - bp - 32768;
						dos.writeShort((short)(diff));
						dos.writeInt(sortedRegionScores[i].stop- sortedRegionScores[i].start);
						dos.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
				//yes short length
				else {
					//write first record's length, subtracting 32768 to extent the range of the signed short
					dos.writeShort((short)(sortedRegionScores[0].stop- sortedRegionScores[0].start - 32768));
					dos.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						//subtract 32768 to extend range of short (-32768 to 32768)
						int diff = currentStart - bp - 32768;
						dos.writeShort((short)(diff));
						dos.writeShort((short)(sortedRegionScores[i].stop- sortedRegionScores[i].start - 32768));
						dos.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
			}

			//no, write int for position
			else {
				//short length? no
				if (useShortLength == false){
					//write first record's length
					dos.writeInt(sortedRegionScores[0].stop- sortedRegionScores[0].start);
					dos.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						int diff = currentStart - bp;
						dos.writeInt(diff);
						dos.writeInt(sortedRegionScores[i].stop- sortedRegionScores[i].start);
						dos.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
				//yes
				else {
					//write first record's length
					dos.writeShort((short)(sortedRegionScores[0].stop- sortedRegionScores[0].start - 32768));
					dos.writeFloat(sortedRegionScores[0].score);
					for (int i=1; i< sortedRegionScores.length; i++){
						int currentStart = sortedRegionScores[i].start;
						int diff = currentStart - bp;
						dos.writeInt(diff);
						dos.writeShort((short)(sortedRegionScores[i].stop- sortedRegionScores[i].start - 32768));
						dos.writeFloat(sortedRegionScores[i].score);
						bp = currentStart;
					}
				}
			}
			//close ZipEntry but not streams!
			out.closeEntry();
		} catch (IOException e) {

			USeqUtilities.safeClose(out);
			USeqUtilities.safeClose(dos);
		} 
	}

	/**Reads a DataInputStream into this RegionScoreData.*/
	public void read (DataInputStream dis) {
		try {
			//read text header, currently not used
			header = dis.readUTF();

			//make array
			int numberRegionScores = sliceInfo.getNumberRecords();
			sortedRegionScores = new RegionScore[numberRegionScores];

			//what kind of data to follow? 
			String fileType = sliceInfo.getBinaryType();

			//int Position, int Length
			if (USeqUtilities.REGION_SCORE_INT_INT_FLOAT.matcher(fileType).matches()){
				//make first RegionScore, position is always an int
				int start = dis.readInt();
				sortedRegionScores[0] = new RegionScore(start, start+dis.readInt(), dis.readFloat());
				//read and resolve offsets to real bps and length to stop
				for (int i=1; i< numberRegionScores; i++){
					start = sortedRegionScores[i-1].start + dis.readInt();
					sortedRegionScores[i] = new RegionScore(start, start + dis.readInt(), dis.readFloat());	
				}
			}
			//int Position, short Length
			else if (USeqUtilities.REGION_SCORE_INT_SHORT_FLOAT.matcher(fileType).matches()){
				//make first RegionScore, position is always an int
				int start = dis.readInt();
				sortedRegionScores[0] = new RegionScore(start, start+ dis.readShort() + 32768, dis.readFloat());
				//read and resolve offsets to real bps and length to stop
				for (int i=1; i< numberRegionScores; i++){
					start = sortedRegionScores[i-1].start + dis.readInt();
					sortedRegionScores[i] = new RegionScore(start, start + dis.readShort() + 32768, dis.readFloat());	
				}
			}
			//short Postion, short Length
			else if (USeqUtilities.REGION_SCORE_SHORT_SHORT_FLOAT.matcher(fileType).matches()){
				//make first RegionScore, position is always an int
				int start = dis.readInt();
				sortedRegionScores[0] = new RegionScore(start, start+ dis.readShort() + 32768, dis.readFloat());
				//read and resolve offsets to real bps and length to stop
				for (int i=1; i< numberRegionScores; i++){
					start = sortedRegionScores[i-1].start + dis.readShort() + 32768;
					sortedRegionScores[i] = new RegionScore(start, start + dis.readShort() + 32768, dis.readFloat());
				}
			}
			//short Position, int Length
			else if (USeqUtilities.REGION_SCORE_SHORT_INT_FLOAT.matcher(fileType).matches()){
				//make first RegionScore, position is always an int
				int start = dis.readInt();
				sortedRegionScores[0] = new RegionScore(start, start+ dis.readInt(), dis.readFloat());
				//read and resolve offsets to real bps and length to stop
				for (int i=1; i< numberRegionScores; i++){
					start = sortedRegionScores[i-1].start + dis.readShort() + 32768;
					sortedRegionScores[i] = new RegionScore(start, start + dis.readInt(), dis.readFloat());	
				}
			}
			//unknown!
			else {
				throw new IOException ("Incorrect file type for creating a RegionScore[] -> '"+fileType+"' in "+binaryFile +"\n");
			}
		} catch (IOException e) {

			USeqUtilities.safeClose(dis);
		}
	}

	public RegionScore[] getRegionScores() {
		return sortedRegionScores;
	}

	public void setRegionScores(RegionScore[] sortedRegionScores) {
		this.sortedRegionScores = sortedRegionScores;
		updateSliceInfo(sortedRegionScores, sliceInfo);
	}

	/**Returns whether data remains.*/
	public boolean trim(int beginningBP, int endingBP) {
		ArrayList<RegionScore> al = new ArrayList<RegionScore>();
		for (int i=0; i< sortedRegionScores.length; i++){
			if (sortedRegionScores[i].isContainedBy(beginningBP, endingBP)) al.add(sortedRegionScores[i]);
		}
		if (al.size() == 0) return false;
		sortedRegionScores = new RegionScore[al.size()];
		al.toArray(sortedRegionScores);
		updateSliceInfo(sortedRegionScores, sliceInfo);
		return true;
	}
}

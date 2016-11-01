package hci.gnomex.utility;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Util {

	// Parses a comma delimited string where commas are ignored if between quotes.
	public static String[] parseCommaDelimited(String s) {
		if (s == null) {
			return new String[0];
		} else {
			String otherThanQuote = " [^\"] ";
			String quotedString = String.format(" \" %s* \" ", otherThanQuote);
			String regex = String.format("(?x) " + // enable comments, ignore white spaces
					",                         " + // match a comma
					"(?=                       " + // start positive look ahead
					"  (                       " + // start group 1
					"    %s*                   " + // match 'otherThanQuote' zero or more times
					"    %s                    " + // match 'quotedString'
					"  )*                      " + // end group 1 and repeat it zero or more times
					"  %s*                     " + // match 'otherThanQuote'
					"  $                       " + // match the end of the string
					")                         ", // stop positive look ahead
					otherThanQuote, quotedString, otherThanQuote);

			String[] tokens = s.split(regex);

			return tokens;
		}
	}

	public static int compareRequestNumbers(String reqNumber1, String reqNumber2) {
		int comp = 0;

		String firstChar1 = getReqFirstChar(reqNumber1);
		String firstChar2 = getReqFirstChar(reqNumber2);

		Integer num1 = getReqNumber(reqNumber1);
		Integer num2 = getReqNumber(reqNumber2);

		if (firstChar1.equals(firstChar2)) {
			comp = num1.compareTo(num2);
		} else {
			comp = firstChar1.compareTo(firstChar2);
		}

		return comp;
	}

	private static String getReqFirstChar(String reqNumber) {
		String c = "0";
		if ("0123456789".indexOf(reqNumber.substring(0, 1)) < 0) {
			c = reqNumber.substring(0, 1);
		}

		return c;
	}

	private static Integer getReqNumber(String reqNumber) {
		String intStr = reqNumber;
		if ("0123456789".indexOf(intStr.substring(0, 1)) < 0) {
			intStr = intStr.substring(1);
		}
		if (intStr.indexOf("R") >= 0) {
			intStr = intStr.substring(0, intStr.indexOf("R"));
		}

		Integer num = Integer.parseInt(intStr);

		return num;
	}

	/*
	 * return the key set of the Map as an array of Strings.
	 */
	public static String[] keysToArray(Map<String, ?> map) {
		String[] keys = new String[map.size()];

		int index = 0;
		for (String key : map.keySet()) {
			keys[index] = key;
			index++;
		}
		return keys;
	}

	/*
	 * Indicates if file is a link file on unix.
	 */
	public static boolean isSymlink(File file) {
		try {
			if (file == null) {
				return false;
			}
			File canon;
			if (file.getParent() == null) {
				canon = file;
			} else {
				File canonDir = file.getParentFile().getCanonicalFile();
				canon = new File(canonDir, file.getName());
			}

			return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
		} catch (IOException ex) {
			return false;
		}
	}

	public static String addURLParameter(String url, String parameter) {
		if (parameter.startsWith("&") || parameter.startsWith("?")) {
			parameter = parameter.substring(1);
		}
		if (url.contains("?")) {
			url += "&";
		} else {
			url += "?";
		}
		url += parameter;
		return url;
	}

	public static String listIntToString(List<Integer> list) {
		if (list == null || list.size() == 0)
			return "";

		boolean firstTime = true;
		String stringList = "";
		for (Integer id : list) {
			if (!firstTime)
				stringList += ",";
			else
				firstTime = false;
			stringList += id;
		}
		return stringList;
	}

	public static String listStrToString(List<String> list) {
		if (list == null || list.size() == 0)
			return "";

		boolean firstTime = true;
		String stringList = "";
		for (String str : list) {
			if (!firstTime)
				stringList += ",";
			else
				firstTime = false;
			stringList += "'" + str + "'";
		}
		return stringList;
	}

	public static void showTime(long start, String info) {
		long endTime = System.currentTimeMillis();
		long numMillis = endTime - start;

		double numsec = numMillis / 1000.0;

		Date d = new Date(System.currentTimeMillis());

		System.out.println(d.toString() + info + numsec + " seconds elapsed time.");

	}

	public static String encodeName(String nameIn) {
		String nameOut = nameIn;
		if (nameOut == null) {
			return nameOut;
		}

		while (nameOut.indexOf('+') >= 0) {
			nameOut = nameOut.replace("+", "%2B");
		}

		return nameOut;

	}

}

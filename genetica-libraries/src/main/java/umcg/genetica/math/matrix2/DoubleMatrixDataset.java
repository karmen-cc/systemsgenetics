/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package umcg.genetica.math.matrix2;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DoubleStatistic;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseLargeDoubleMatrix2D;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import umcg.genetica.io.text.TextFile;

/**
 *
 * @author MarcJan, Juha, Harm-Jan, Patrick
 * @param <R>
 * @param <C>
 */
public class DoubleMatrixDataset<R extends Comparable, C extends Comparable> {

	static final Logger LOGGER = Logger.getLogger(DoubleMatrixDataset.class.getName());

	protected DoubleMatrix2D matrix;
	protected LinkedHashMap<R, Integer> hashRows;
	protected LinkedHashMap<C, Integer> hashCols;

	public DoubleMatrixDataset() {
		hashRows = new LinkedHashMap<R, Integer>();
		hashCols = new LinkedHashMap<C, Integer>();
	}

	public DoubleMatrixDataset(int rows, int columns) {
		hashRows = new LinkedHashMap<R, Integer>((int) Math.ceil(rows / 0.75));
		hashCols = new LinkedHashMap<C, Integer>((int) Math.ceil(columns / 0.75));
		if ((rows * (long) columns) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(rows, columns);
		} else {
			matrix = new DenseLargeDoubleMatrix2D(rows, columns);
		}
	}

	public DoubleMatrixDataset(LinkedHashMap<R, Integer> hashRows, LinkedHashMap<C, Integer> hashCols) {
		this.hashRows = hashRows;
		this.hashCols = hashCols;
		if ((hashRows.size() * (long) hashCols.size()) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(hashRows.size(), hashCols.size());
		} else {
			matrix = new DenseLargeDoubleMatrix2D(hashRows.size(), hashCols.size());
		}
	}

	public DoubleMatrixDataset(DoubleMatrix2D matrix, LinkedHashMap<R, Integer> hashRows, LinkedHashMap<C, Integer> hashCols) {
		this.hashRows = hashRows;
		this.hashCols = hashCols;
		this.matrix = matrix;
	}

	public DoubleMatrixDataset(Collection<R> rowNames, Collection<C> colNames) {

		hashRows = new LinkedHashMap<R, Integer>(rowNames.size());
		hashCols = new LinkedHashMap<C, Integer>(colNames.size());

		int i = 0;
		for (R row : rowNames) {
			hashRows.put(row, i);
			++i;
		}

		i = 0;
		for (C col : colNames) {
			hashCols.put(col, i);
			++i;
		}

		if ((hashRows.size() * (long) hashCols.size()) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(hashRows.size(), hashCols.size());
		} else {
			matrix = new DenseLargeDoubleMatrix2D(hashRows.size(), hashCols.size());
		}

	}

	/**
	 * Not making double matrix for 2D array is not very efficient. Try creating
	 * a DoubleMatrixDataset directly.
	 *
	 * @param matrix
	 * @param rowNames
	 * @param colNames
	 * @throws Exception
	 */
	public DoubleMatrixDataset(double[][] matrix, R[] rowNames, C[] colNames) throws Exception {
		this(matrix, Arrays.asList(rowNames), Arrays.asList(colNames));
	}

	/**
	 * Not making double matrix for 2D array is not very efficient. Try creating
	 * a DoubleMatrixDataset directly.
	 *
	 * @param matrix
	 * @param rowNames
	 * @param colNames
	 * @throws Exception
	 */
	public DoubleMatrixDataset(double[][] matrix, List<R> rowNames, List<C> colNames) throws Exception {

		if (matrix.length == 0) {
			throw new Exception("Can't create dataset matrix with no dimensions");
		}

		if (rowNames.size() != matrix.length) {
			throw new Exception("Row names not same size as matrix");
		}

		if (colNames.size() != matrix[0].length) {
			throw new Exception("Col names not same size as matrix");
		}

		hashRows = new LinkedHashMap<>(rowNames.size());
		hashCols = new LinkedHashMap<>(colNames.size());

		int i = 0;
		for (R row : rowNames) {
			if (hashRows.put(row, i) != null) {
				throw new Exception("Duplicate row names not allowed: " + row.toString());
			}
			++i;
		}

		i = 0;
		for (C col : colNames) {
			if (hashCols.put(col, i) != null) {
				throw new Exception("Duplicate col names not allowed: " + col.toString());
			}
			++i;
		}

		this.matrix = new DenseDoubleMatrix2D(matrix);

	}

	public static DoubleMatrixDataset<String, String> loadDoubleData(String fileName) throws IOException, Exception {
		if ((fileName.endsWith(".txt") || fileName.endsWith(".tsv") || fileName.endsWith(".txt.gz"))) {
			return loadDoubleTextData(fileName, '\t');
		} else if (fileName.endsWith(".binary")) {
			return loadDoubleBinaryData(fileName);
		} else {
			throw new IllegalArgumentException("File type must be \".txt\", \".tsv\" or \".txt.gz\" when delimiter is set to: \"tab\" \n Input filename: " + fileName);
		}
	}
	
	public static List<String> readDoubleTextDataColNames(String fileName, char delimiter) throws IOException{
		if (!(fileName.endsWith(".txt") || fileName.endsWith(".tsv") || fileName.endsWith(".txt.gz"))) {
			throw new IllegalArgumentException("File type must be \".txt\", \".tsv\" or \".txt.gz\" when delimiter is set. \n Input filename: " + fileName);
		}

		//Pattern splitPatern = Pattern.compile(delimiter);
		int columnOffset = 1;

		TextFile in = new TextFile(fileName, TextFile.R);
		String str = in.readLine(); // header
		String[] data = StringUtils.splitPreserveAllTokens(str, delimiter);

		int tmpCols = (data.length - columnOffset);

		ArrayList<String> colNames = new ArrayList<>(tmpCols);
		
		for (int s = 0; s < tmpCols; s++) {
			String colName = data[s + columnOffset];
			colNames.add(colName);
		}
		
		return colNames;

	}
	
	public static List<String> readDoubleTextDataRowNames(String fileName, char delimiter) throws IOException{
		if (!(fileName.endsWith(".txt") || fileName.endsWith(".tsv") || fileName.endsWith(".txt.gz"))) {
			throw new IllegalArgumentException("File type must be \".txt\", \".tsv\" or \".txt.gz\" when delimiter is set. \n Input filename: " + fileName);
		}

		//Pattern splitPatern = Pattern.compile(delimiter);
		int columnOffset = 1;

		TextFile in = new TextFile(fileName, TextFile.R);
		String str = in.readLine(); // header
		String[] data = StringUtils.splitPreserveAllTokens(str, delimiter);

		int tmpCols = (data.length - columnOffset);

		ArrayList<String> rowNames = new ArrayList<>(tmpCols);

		while ((str = in.readLine()) != null) {
			data = StringUtils.splitPreserveAllTokens(str, delimiter);
			rowNames.add(data[0]);
		}
		
		return rowNames;

	}

	public static DoubleMatrixDataset<String, String> loadDoubleTextData(String fileName, char delimiter) throws IOException, Exception {
		if (!(fileName.endsWith(".txt") || fileName.endsWith(".tsv") || fileName.endsWith(".txt.gz"))) {
			throw new IllegalArgumentException("File type must be \".txt\", \".tsv\" or \".txt.gz\" when delimiter is set. \n Input filename: " + fileName);
		}

		//Pattern splitPatern = Pattern.compile(delimiter);
		int columnOffset = 1;

		TextFile in = new TextFile(fileName, TextFile.R);
		String str = in.readLine(); // header
		String[] data = StringUtils.splitPreserveAllTokens(str, delimiter);

		int tmpCols = (data.length - columnOffset);

		LinkedHashMap<String, Integer> colMap = new LinkedHashMap<String, Integer>((int) Math.ceil(tmpCols / 0.75));

		for (int s = 0; s < tmpCols; s++) {
			String colName = data[s + columnOffset];
			if (!colMap.containsKey(colName)) {
				colMap.put(colName, s);
			} else {
				LOGGER.warning("Duplicated column name!");
				throw (new Exception("Duplicated column names found in: " + fileName));
			}
		}

		int tmpRows = 0;

		while (in.readLine() != null) {
			tmpRows++;
		}
		in.close();

		LinkedHashMap<String, Integer> rowMap = new LinkedHashMap<String, Integer>((int) Math.ceil(tmpRows / 0.75));
		DoubleMatrix2D tmpMatrix;

		if ((tmpRows * (long) tmpCols) < (Integer.MAX_VALUE - 2)) {
			tmpMatrix = new DenseDoubleMatrix2D(tmpRows, tmpCols);
		} else {
			tmpMatrix = new DenseLargeDoubleMatrix2D(tmpRows, tmpCols);
		}
		in.open();
		in.readLine(); // read header
		int row = 0;

		boolean correctData = true;
		while ((str = in.readLine()) != null) {
			data = StringUtils.splitPreserveAllTokens(str, delimiter);

			if (!rowMap.containsKey(data[0])) {
				rowMap.put(data[0], row);
				for (int s = 0; s < tmpCols; s++) {
					double d;
					try {
						d = Double.parseDouble(data[s + columnOffset]);
					} catch (NumberFormatException e) {
						correctData = false;
						d = Double.NaN;
					}
					tmpMatrix.setQuick(row, s, d);
				}
				row++;
			} else {
				LOGGER.warning("Duplicated row name!");
				throw (new Exception("Duplicated row names found in: " + fileName));
			}

		}
		if (!correctData) {
			LOGGER.warning("Your data contains NaN/unparseable values!");
		}
		in.close();

		DoubleMatrixDataset<String, String> dataset = new DoubleMatrixDataset<String, String>(tmpMatrix, rowMap, colMap);

		LOGGER.log(Level.INFO, "''{0}'' has been loaded, nrRows: {1} nrCols: {2}", new Object[]{fileName, dataset.matrix.rows(), dataset.matrix.columns()});
		return dataset;
	}

	public static DoubleMatrixDataset<String, String> loadSubsetOfRowsBinaryDoubleData(String fileName, String[] rowsToView) throws IOException, Exception {
		return loadSubsetOfRowsBinaryDoubleData(fileName, Arrays.asList(rowsToView));
	}
	
	public static DoubleMatrixDataset<String, String> loadSubsetOfRowsBinaryDoubleData(String fileName, Collection<String> rowsToView) throws IOException, Exception {

		LinkedHashSet<String> rowsToViewHash = new LinkedHashSet<>(rowsToView.size());

		for (String rowToView : rowsToView) {
			rowsToViewHash.add(rowToView);
		}

		if (rowsToViewHash.size() != rowsToView.size()) {

			StringBuilder duplicateRowsRequested = new StringBuilder();

			HashSet<String> rowsSeen = new HashSet<>();

			for (String rowToView : rowsToView) {

				if (!rowsSeen.add(rowToView)) {
					duplicateRowsRequested.append(rowToView);
					duplicateRowsRequested.append(";");
				}

			}

			throw new Exception("Duplicates in rows not allowed. Requested duplicate values: " + duplicateRowsRequested);
		}

		return loadSubsetOfRowsBinaryDoubleData(fileName, rowsToViewHash);

	}

	/**
	 *
	 *
	 * @param fileName
	 * @param rowsToView
	 * @return subset of rows in order of rowsToView
	 * @throws IOException
	 */
	public static DoubleMatrixDataset<String, String> loadSubsetOfRowsBinaryDoubleData(String fileName, LinkedHashSet<String> rowsToView) throws IOException {

		//Now load the row and column identifiers from files
		LinkedHashMap<String, Integer> originalRowMap = loadIdentifiers(fileName + ".rows.txt");
		LinkedHashMap<String, Integer> originalColMap = loadIdentifiers(fileName + ".cols.txt");

		return loadSubsetOfRowsBinaryDoubleData(fileName, rowsToView, originalRowMap, originalColMap);

	}

	/**
	 * Internal function. Does not check if proper original row/col map is used
	 *
	 * @param fileName
	 * @param rowsToView
	 * @param originalRowMap
	 * @param originalColMap
	 * @return subset of rows in order of rowsToView
	 * @throws IOException
	 */
	protected static DoubleMatrixDataset<String, String> loadSubsetOfRowsBinaryDoubleData(String fileName, LinkedHashSet<String> rowsToView, LinkedHashMap<String, Integer> originalRowMap, LinkedHashMap<String, Integer> originalColMap) throws IOException {

		LinkedHashMap<String, Integer> rowMap = new LinkedHashMap<>(rowsToView.size());

		DoubleMatrix2D matrix;

		for (String rowToView : rowsToView) {
			if (!originalRowMap.containsKey(rowToView)) {
				throw new RuntimeException("Matrix at: " + fileName + " does not contain this row: " + rowToView);
			}
		}

		if ((rowsToView.size() * (long) originalColMap.size()) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(rowsToView.size(), originalColMap.size());
		} else {
			matrix = new DenseLargeDoubleMatrix2D(rowsToView.size(), originalColMap.size());
		}

		File fileBinary = new File(fileName + ".dat");
		RandomAccessFile in = new RandomAccessFile(fileBinary, "r");
		int nrRows;
		int nrCols;
		byte[] bytes = new byte[4];
		in.read(bytes, 0, 4);
		nrRows = byteArrayToInt(bytes);
		in.read(bytes, 0, 4);
		nrCols = byteArrayToInt(bytes);

		if (nrRows != originalRowMap.size()) {
			throw new RuntimeException("Matrix at: " + fileName + " does not have expected number of rows");
		}

		if (nrCols != originalColMap.size()) {
			throw new RuntimeException("Matrix at: " + fileName + " does not have expected number of cols");
		}

		byte[] buffer = new byte[nrCols * 8];

		long rowLength = 8l * nrCols;
		long bits;

		int currentRowInSubset = 0;
		for (String rowToView : rowsToView) {

			rowMap.put(rowToView, currentRowInSubset);

			long rowInFullMatrix = originalRowMap.get(rowToView);

			in.seek(8 + (rowLength * rowInFullMatrix));

			in.read(buffer, 0, nrCols * 8);
			int bufferLoc = 0;
			for (int col = 0; col < nrCols; col++) {
				bits = (long) (0xff & buffer[bufferLoc + 7])
						| (long) (0xff & buffer[bufferLoc + 6]) << 8
						| (long) (0xff & buffer[bufferLoc + 5]) << 16
						| (long) (0xff & buffer[bufferLoc + 4]) << 24
						| (long) (0xff & buffer[bufferLoc + 3]) << 32
						| (long) (0xff & buffer[bufferLoc + 2]) << 40
						| (long) (0xff & buffer[bufferLoc + 1]) << 48
						| (long) (buffer[bufferLoc]) << 56;

				matrix.setQuick(currentRowInSubset, col, Double.longBitsToDouble(bits));
				bufferLoc += 8;
			}
			currentRowInSubset++;

		}
		in.close();
		return new DoubleMatrixDataset<>(matrix, rowMap, originalColMap);

	}

	/**
	 *
	 * @param fileName
	 * @param desiredRows
	 * @param desiredCols
	 * @return
	 * @throws IOException
	 * @deprecated Untested. For now use loadSubsetOfRowsBinaryDoubleData and
	 * then do viewColSelection. That option will keep all cols in memory
	 */
	public static DoubleMatrixDataset<String, String> loadSubsetOfBinaryDoubleData(String fileName, HashSet<String> desiredRows, HashSet<String> desiredCols) throws IOException {

		//Now load the row and column identifiers from files
		LinkedHashMap<String, Integer> rowMap = loadIdentifiers(fileName + ".rows.txt");
		LinkedHashMap<String, Integer> colMap = loadIdentifiers(fileName + ".cols.txt");

		// determine which rows to include
		LinkedHashMap<String, Integer> newRowMap = null;
		HashSet<Integer> requestedRows = null;
		if (desiredRows != null) {
			requestedRows = new HashSet<>();
			newRowMap = new LinkedHashMap<>();
			int rctr = 0;
			for (String key : rowMap.keySet()) {
				if (desiredRows.contains(key)) {
					requestedRows.add(rowMap.get(key));
					newRowMap.put(key, rctr);
					rctr++;
				}
			}
		} else {
			newRowMap = rowMap;
		}

		// determine which columns to include
		LinkedHashMap<String, Integer> newColMap = null;
		HashSet<Integer> requestedCols = null;
		if (desiredCols != null) {
			requestedCols = new HashSet<>();
			newColMap = new LinkedHashMap<>();
			int cctr = 0;
			for (String key : colMap.keySet()) {
				if (desiredCols.contains(key)) {
					requestedCols.add(rowMap.get(key));
					newColMap.put(key, cctr);
					cctr++;
				}
			}
		} else {
			newColMap = colMap;
		}

		//First load the raw binary data:
		File fileBinary = new File(fileName + ".dat");
		BufferedInputStream in;
		int nrRows;
		int nrCols;
		in = new BufferedInputStream(new FileInputStream(fileBinary));
		byte[] bytes = new byte[4];
		in.read(bytes, 0, 4);
		nrRows = byteArrayToInt(bytes);
		in.read(bytes, 0, 4);
		nrCols = byteArrayToInt(bytes);

		int reqrows = nrRows;
		int reqcols = nrCols;
		if (requestedRows != null) {
			reqrows = requestedRows.size();
		}
		if (requestedCols != null) {
			reqcols = requestedCols.size();
		}

		DoubleMatrix2D matrix;
		if ((reqrows * (long) reqcols) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(reqrows, reqcols);
		} else {
			matrix = new DenseLargeDoubleMatrix2D(reqrows, reqcols);
		}

		byte[] buffer = new byte[nrCols * 8];
		long bits;

		int rctr = -1;

		for (int row = 0; row < nrRows; row++) {
			in.read(buffer, 0, nrCols * 8);
			int bufferLoc = 0;

			if (requestedRows == null || requestedRows.contains(row)) {
				rctr++;
				int cctr = 0;
				for (int col = 0; col < nrCols; col++) {
					bits = (long) (0xff & buffer[bufferLoc + 7])
							| (long) (0xff & buffer[bufferLoc + 6]) << 8
							| (long) (0xff & buffer[bufferLoc + 5]) << 16
							| (long) (0xff & buffer[bufferLoc + 4]) << 24
							| (long) (0xff & buffer[bufferLoc + 3]) << 32
							| (long) (0xff & buffer[bufferLoc + 2]) << 40
							| (long) (0xff & buffer[bufferLoc + 1]) << 48
							| (long) (buffer[bufferLoc]) << 56;

					if (requestedCols == null || requestedCols.contains(col)) {
						matrix.setQuick(rctr, cctr, Double.longBitsToDouble(bits));
						cctr++;
					}

					bufferLoc += 8;
				}
			}

		}
		in.close();

		DoubleMatrixDataset<String, String> dataset = new DoubleMatrixDataset<String, String>(matrix, newRowMap, newColMap);
		LOGGER.log(Level.INFO, "Binary file ''{0}'' has been loaded, nrRows: {1} nrCols: {2}", new Object[]{fileName, reqrows, reqcols});

		return dataset;

	}

	public static DoubleMatrixDataset<String, String> loadSubsetOfTextDoubleData(String fileName, char delimiter, HashSet<String> desiredRows, HashSet<String> desiredCols) throws IOException, Exception {
		if (!(fileName.endsWith(".txt") || fileName.endsWith(".txt.gz") || fileName.endsWith(".tsv") || fileName.endsWith(".tsv.gz"))) {
			throw new IllegalArgumentException("File type must be .txt or .tsv when delimiter is given (given filename: " + fileName + ")");
		}

		LinkedHashSet<Integer> desiredColPos = new LinkedHashSet<Integer>();

		//Pattern splitPatern = Pattern.compile(delimiter);
		int columnOffset = 1;

		TextFile in = new TextFile(fileName, TextFile.R);
		String str = in.readLine(); // header
		String[] data = StringUtils.splitPreserveAllTokens(str, delimiter);

		int tmpCols = (data.length - columnOffset);

		LinkedHashMap<String, Integer> colMap = new LinkedHashMap<String, Integer>((int) Math.ceil(tmpCols / 0.75));

		int storedCols = 0;
		for (int s = 0; s < tmpCols; s++) {
			String colName = data[s + columnOffset];
			if (!colMap.containsKey(colName) && (desiredCols == null || desiredCols.contains(colName) || desiredCols.isEmpty())) {
				colMap.put(colName, storedCols);
				desiredColPos.add((s));
				storedCols++;
			} else if (colMap.containsKey(colName)) {
				LOGGER.warning("Duplicated column name!");
				throw new Exception("Duplicated column are not allowed. Tried to add: " + colName);
			}
		}

		LinkedHashSet<Integer> desiredRowPos = new LinkedHashSet<Integer>();
		int rowsToStore = 0;
		int totalRows = 0;
		//System.out.println(desiredRows.toString());
		while ((str = in.readLine()) != null) {
			String[] info = StringUtils.splitPreserveAllTokens(str, delimiter);
			if (desiredRows == null || desiredRows.contains(info[0]) || desiredRows.isEmpty()) {
				rowsToStore++;
				desiredRowPos.add(totalRows);
			}
			totalRows++;
		}
		in.close();

		DoubleMatrix2D matrix;
		if ((rowsToStore * (long) tmpCols) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(rowsToStore, storedCols);
		} else {
			matrix = new DenseLargeDoubleMatrix2D(rowsToStore, storedCols);
		}

		in.open();
		in.readLine(); // read header
		int storingRow = 0;
		totalRows = 0;
		LinkedHashMap<String, Integer> rowMap = new LinkedHashMap<String, Integer>((int) Math.ceil(rowsToStore / 0.75));

		boolean correctData = true;
		while ((str = in.readLine()) != null) {

			if (desiredRowPos.contains(totalRows)) {
				data = StringUtils.splitPreserveAllTokens(str, delimiter);
				if (!rowMap.containsKey(data[0])) {
					rowMap.put(data[0], storingRow);
					int storingCol = 0;
					for (int s : desiredColPos) {
						double d;
						try {
							d = Double.parseDouble(data[s + columnOffset]);
						} catch (NumberFormatException e) {
							correctData = false;
							d = Double.NaN;
						}
						matrix.setQuick(storingRow, storingCol, d);
						storingCol++;
					}
					storingRow++;
				} else if (rowMap.containsKey(data[0])) {
					LOGGER.warning("Duplicated row name!");
					throw new Exception("Duplicated row are not allowed. Tried to add: " + data[0]);
				}
			}
			totalRows++;
		}
		if (!correctData) {
			LOGGER.warning("Your data contains NaN/unparseable values!");
		}
		in.close();

		DoubleMatrixDataset<String, String> dataset = new DoubleMatrixDataset<String, String>(matrix, rowMap, colMap);

		LOGGER.log(Level.INFO, "''{0}'' has been loaded, nrRows: {1} nrCols: {2}", new Object[]{fileName, dataset.matrix.rows(), dataset.matrix.columns()});
		return dataset;
	}

	public static DoubleMatrixDataset<String, String> loadDoubleTextDoubleDataExlcudeCols(String fileName, char delimiter, HashSet<String> colsToExclude) throws IOException, Exception {

		TextFile in = new TextFile(fileName, TextFile.R);
		String str = in.readLine(); // header
		String[] data = StringUtils.splitPreserveAllTokens(str, delimiter);

		HashSet<String> desiredCols = new HashSet<>();

		for (String colName : data) {

			if (!colsToExclude.contains(colName)) {
				desiredCols.add(colName);
			}

		}

		return DoubleMatrixDataset.loadSubsetOfTextDoubleData(fileName, delimiter, null, desiredCols);

	}

	/**
	 *
	 * @param fileName excluding .dat
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static DoubleMatrixDataset<String, String> loadTransEqtlExpressionMatrix(String fileName) throws FileNotFoundException, IOException {

		File matrix = new File(fileName + ".dat");
		File probeFile = new File(fileName + "-ColNames.txt.gz");
		File snpFile = new File(fileName + "-RowNames.txt.gz");

		LinkedHashMap<String, Integer> rowMap = new LinkedHashMap<>();
		LinkedHashMap<String, Integer> colMap = new LinkedHashMap<>();

		final CSVParser parser = new CSVParserBuilder().withSeparator('\t').withIgnoreQuotations(true).build();
		final CSVReader probeReader = new CSVReaderBuilder(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(probeFile))))).withSkipLines(0).withCSVParser(parser).build();
		final CSVReader snpReader = new CSVReaderBuilder(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(snpFile))))).withSkipLines(1).withCSVParser(parser).build();

		String[] nextLine;
		int nrCols = 0;
		while ((nextLine = probeReader.readNext()) != null) {
			if (colMap.put(nextLine[0], nrCols++) != null) {
				throw new RuntimeException("Duplicate col names not allowed: " + nextLine[0]);
			}
		}

		int nrRows = 0;
		while ((nextLine = snpReader.readNext()) != null) {
			if (rowMap.put(nextLine[0], nrRows++) != null) {
				throw new RuntimeException("Duplicate row names not allowed: " + nextLine[0]);
			}
		}

		System.out.println("Number of cols in " + probeFile.getName() + ": " + nrCols);
		System.out.println("Number of rows in " + snpFile.getName() + ": " + nrRows);

		DoubleMatrixDataset<String, String> dataset = new DoubleMatrixDataset<>(rowMap, colMap);

		DataInputStream dis = new DataInputStream(new FileInputStream(matrix));
		int magicNumber = dis.readInt();
		if (magicNumber == 1) {
			throw new RuntimeException("Cannot read cis matrix");
		} else if (magicNumber > 1) {
			throw new RuntimeException("Invalid magic number");
		}

		for (int r = 0; r < nrRows; ++r) {

			for (int c = 0; c < nrCols; ++c) {

				dataset.setElementQuick(r, c, dis.readFloat());

			}

		}

		System.out.println("Done loading eQTL result matrix");

		return dataset;

	}

	public static DoubleMatrixDataset<String, String> loadDoubleBinaryData(String fileName) throws FileNotFoundException, IOException {
		//First load the raw binary data:
		File fileBinary = new File(fileName + ".dat");
		BufferedInputStream in;
		int nrRows;
		int nrCols;
		in = new BufferedInputStream(new FileInputStream(fileBinary));
		byte[] bytes = new byte[4];
		in.read(bytes, 0, 4);
		nrRows = byteArrayToInt(bytes);
		in.read(bytes, 0, 4);
		nrCols = byteArrayToInt(bytes);

		DoubleMatrix2D matrix;
		if ((nrRows * (long) nrCols) < (Integer.MAX_VALUE - 2)) {
			matrix = new DenseDoubleMatrix2D(nrRows, nrCols);
		} else {
			matrix = new DenseLargeDoubleMatrix2D(nrRows, nrCols);
		}

		//Now load the row and column identifiers from files
		LinkedHashMap<String, Integer> rowMap = loadIdentifiers(fileName + ".rows.txt");
		LinkedHashMap<String, Integer> colMap = loadIdentifiers(fileName + ".cols.txt");

		if (nrRows != rowMap.size()) {
			throw new RuntimeException("Matrix at: " + fileName + " does not have expected number of rows");
		}

		if (nrCols != colMap.size()) {
			throw new RuntimeException("Matrix at: " + fileName + " does not have expected number of cols");
		}

		byte[] buffer = new byte[nrCols * 8];
		long bits;
		for (int row = 0; row < nrRows; row++) {
			in.read(buffer, 0, nrCols * 8);
			int bufferLoc = 0;
			for (int col = 0; col < nrCols; col++) {
				bits = (long) (0xff & buffer[bufferLoc + 7])
						| (long) (0xff & buffer[bufferLoc + 6]) << 8
						| (long) (0xff & buffer[bufferLoc + 5]) << 16
						| (long) (0xff & buffer[bufferLoc + 4]) << 24
						| (long) (0xff & buffer[bufferLoc + 3]) << 32
						| (long) (0xff & buffer[bufferLoc + 2]) << 40
						| (long) (0xff & buffer[bufferLoc + 1]) << 48
						| (long) (buffer[bufferLoc]) << 56;

				matrix.setQuick(row, col, Double.longBitsToDouble(bits));
				bufferLoc += 8;
			}
		}
		in.close();

		DoubleMatrixDataset<String, String> dataset = new DoubleMatrixDataset<String, String>(matrix, rowMap, colMap);
		LOGGER.log(Level.INFO, "Binary file ''{0}'' has been loaded, nrRows: {1} nrCols: {2}", new Object[]{fileName, nrRows, nrCols});

		return dataset;
	}

	public void saveBinary(String path) throws IOException {

		final File matrixFile = new File(path + ".dat");
		final File rowFile = new File(path + ".rows.txt");
		final File colFile = new File(path + ".cols.txt");

		final String[] outputLine = new String[1];

		final CSVWriter rowWriter = new CSVWriter(new FileWriter(rowFile), '\t', '\0', '\0', "\n");
		for (R row : hashRows.keySet()) {
			outputLine[0] = row.toString();
			rowWriter.writeNext(outputLine);
		}
		rowWriter.close();

		final CSVWriter colWriter = new CSVWriter(new FileWriter(colFile), '\t', '\0', '\0', "\n");
		for (C col : hashCols.keySet()) {
			outputLine[0] = col.toString();
			colWriter.writeNext(outputLine);
		}
		colWriter.close();

		final int rows = rows();
		final int cols = columns();

		final BufferedOutputStream matrixWriter = new BufferedOutputStream(new FileOutputStream(matrixFile));
		matrixWriter.write(intToByteArray(rows));
		matrixWriter.write(intToByteArray(cols));
		byte[] buffer = new byte[cols * 8];
		for (int row = 0; row < rows; row++) { // rows
			int bufferLoc = 0;
			for (int col = 0; col < cols; col++) { // columns
				long bits = Double.doubleToLongBits(matrix.getQuick(row, col));
				buffer[bufferLoc++] = (byte) (bits >> 56);
				buffer[bufferLoc++] = (byte) (bits >> 48 & 0xff);
				buffer[bufferLoc++] = (byte) (bits >> 40 & 0xff);
				buffer[bufferLoc++] = (byte) (bits >> 32 & 0xff);
				buffer[bufferLoc++] = (byte) (bits >> 24 & 0xff);
				buffer[bufferLoc++] = (byte) (bits >> 16 & 0xff);
				buffer[bufferLoc++] = (byte) (bits >> 8 & 0xff);
				buffer[bufferLoc++] = (byte) (bits & 0xff);
			}
			matrixWriter.write(buffer);
		}
		matrixWriter.close();

	}

	protected static LinkedHashMap<String, Integer> loadIdentifiers(String filename) throws IOException {
		TextFile tf = new TextFile(filename, false);
		String[] rowsArr = tf.readAsArray();
		tf.close();
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		for (String row : rowsArr) {
			map.put(row, map.size());
		}
		return map;
	}

	public void save(File file, String rowDescriptor) throws IOException {
		TextFile out = new TextFile(file, TextFile.W);

		out.append(rowDescriptor);
		for (C col : hashCols.keySet()) {

			out.append('\t');
			out.append(col.toString());
		}
		out.append('\n');
		int r = 0;
		for (R row : hashRows.keySet()) {
			out.append(row.toString());
			for (int c = 0; c < matrix.columns(); c++) {
				out.append('\t');
				out.append(String.valueOf(matrix.getQuick(r, c)));
			}
			out.append('\n');
			++r;
		}
		out.close();
	}

	public void save(String fileName) throws IOException {
		save(new File(fileName), "-");
	}

	public void save(File fileName) throws IOException {
		save(fileName, "-");
	}

	public void save(String fileName, String rowDescriptor) throws IOException {
		save(new File(fileName), rowDescriptor);
	}

	public void saveDice(String fileName) throws IOException {
		saveDice(new File(fileName), "-");
	}

	public void saveDice(File fileName) throws IOException {
		saveDice(fileName, "-");
	}

	public void saveDice(String fileName, String rowDescriptor) throws IOException {
		saveDice(new File(fileName), rowDescriptor);
	}

	public void saveDice(File fileName, String rowDescriptor) throws IOException {
		TextFile out = new TextFile(fileName, TextFile.W);

		out.append(rowDescriptor);
		for (R row : hashRows.keySet()) {
			out.append('\t');
			out.append(row.toString());
		}
		out.append('\n');

		int c = 0;
		for (C col : hashCols.keySet()) {
			out.append(col.toString());
			for (int r = 0; r < matrix.rows(); r++) {

				out.append('\t');
				out.append(String.valueOf(matrix.getQuick(r, c)));
			}
			out.append('\n');
			++c;
		}
		out.close();
	}

	private static byte[] intToByteArray(int value) {
		return new byte[]{(byte) (value >>> 24),
			(byte) (value >>> 16),
			(byte) (value >>> 8),
			(byte) value};
	}

	private static int byteArrayToInt(byte[] b) {
		return (b[0] << 24)
				+ ((b[1] & 0xff) << 16)
				+ ((b[2] & 0xff) << 8)
				+ (b[3] & 0xff);
	}

	//Getters and setters
	/**
	 *
	 * @return Number of rows
	 */
	public int rows() {
		return matrix.rows();
	}

	public int columns() {
		return matrix.columns();
	}

	public LinkedHashMap<R, Integer> getHashRows() {
		return hashRows;
	}

	public void setHashRows(LinkedHashMap<R, Integer> hashRows) {
		this.hashRows = hashRows;
	}

	public LinkedHashMap<C, Integer> getHashCols() {
		return hashCols;
	}

	public void setHashCols(LinkedHashMap<C, Integer> hashCols) {
		this.hashCols = hashCols;
	}

	public ArrayList<R> getRowObjects() {
		return new ArrayList<>(hashRows.keySet());
	}

	public LinkedHashMap<C, Integer> getHashColsCopy() {
		return new LinkedHashMap<>(hashCols);
	}

	public void setRowObjects(List<R> arrayList) throws Exception {
		LinkedHashMap<R, Integer> newHashRows = new LinkedHashMap<R, Integer>((int) Math.ceil(arrayList.size() / 0.75));
		int i = 0;
		for (R s : arrayList) {
			if (!newHashRows.containsKey(s)) {
				newHashRows.put(s, i);
			} else {
				throw new Exception("Error, new row names contains dupilcates.");
			}
			i++;
		}

		this.hashRows = newHashRows;
	}

	public ArrayList<C> getColObjects() {
		return new ArrayList<C>(hashCols.keySet());
	}

	public void setColObjects(List<C> arrayList) throws Exception {
		LinkedHashMap<C, Integer> newHashCols = new LinkedHashMap<C, Integer>((int) Math.ceil(arrayList.size() / 0.75));
		int i = 0;
		for (C s : arrayList) {
			if (!newHashCols.containsKey(s)) {
				newHashCols.put(s, i);
			} else {
				throw new Exception("Error, new col names contains dupilcates.");
			}
			i++;
		}
		this.hashCols = newHashCols;
	}

	public DoubleMatrix2D getMatrix() {
		return matrix;
	}

	public void setMatrix(DoubleMatrix2D matrix) {
		this.matrix = matrix;
	}

	public void setMatrix(double[][] matrix) {
		if ((matrix.length * (long) matrix[0].length) < (Integer.MAX_VALUE - 2)) {
			this.matrix = new DenseDoubleMatrix2D(matrix);
		} else {
			this.matrix = new DenseLargeDoubleMatrix2D(matrix.length, matrix[0].length);
			this.matrix.assign(matrix);
		}
	}

	/**
	 * NOT recommended. Will make full copy of the data.
	 *
	 * @return
	 */
	public double[][] getMatrixAs2dDoubleArray() {
		return this.matrix.toArray();
	}

	/**
	 * Order columns
	 *
	 */
	public void OrderOnColumnnames() {
		LinkedHashMap<C, Integer> newColHash = new LinkedHashMap<C, Integer>((int) Math.ceil(this.matrix.columns() / 0.75));
		ArrayList<C> names = this.getColObjects();
		Collections.sort(names);

		int pos = 0;
		for (C name : names) {
			newColHash.put(name, pos);
			pos++;
		}
		reorderCols(newColHash);
	}

	/**
	 * Order rows
	 *
	 */
	public void OrderOnRownames() {
		LinkedHashMap<R, Integer> newRowHash = new LinkedHashMap<R, Integer>((int) Math.ceil(this.matrix.rows() / 0.75));
		ArrayList<R> names = this.getRowObjects();
		Collections.sort(names);

		int pos = -1;
		for (R name : names) {
			pos++;
			newRowHash.put(name, pos);
		}
		reorderRows(newRowHash);

	}

	public void reorderRows(LinkedHashMap<R, Integer> mappingIndex) {
		boolean equal = compareHashRows(mappingIndex, this.hashRows);
		if (!equal) {
			DoubleMatrix2D newRawData;
			if ((this.rows() * (long) this.columns()) < (Integer.MAX_VALUE - 2)) {
				newRawData = new DenseDoubleMatrix2D(this.rows(), this.columns());
			} else {
				newRawData = new DenseLargeDoubleMatrix2D(this.rows(), this.columns());
			}

			for (Map.Entry<R, Integer> ent : mappingIndex.entrySet()) {
				int pos = this.getHashRows().get(ent.getKey());
				for (int s = 0; s < this.columns(); ++s) {
					newRawData.set(ent.getValue(), s, this.getMatrix().get(pos, s));
				}
			}
			this.setHashRows(mappingIndex);
			this.setMatrix(newRawData);
		}

	}

	public void reorderCols(LinkedHashMap<C, Integer> mappingIndex) {
		boolean equal = compareHashCols(mappingIndex, this.hashCols);
		if (!equal) {
			DoubleMatrix2D newRawData;
			if ((this.rows() * (long) this.columns()) < (Integer.MAX_VALUE - 2)) {
				newRawData = new DenseDoubleMatrix2D(this.rows(), this.columns());
			} else {
				newRawData = new DenseLargeDoubleMatrix2D(this.rows(), this.columns());
			}

			for (Map.Entry<C, Integer> ent : mappingIndex.entrySet()) {
				int pos = this.getHashCols().get(ent.getKey());
				for (int p = 0; p < this.rows(); ++p) {
					newRawData.set(p, ent.getValue(), this.getMatrix().get(p, pos));
				}
			}

			this.setHashCols(mappingIndex);
			this.setMatrix(newRawData);
		}
	}

	public DoubleMatrixDataset<C, R> viewDice() {
		return new DoubleMatrixDataset<C, R>(matrix.viewDice(), hashCols, hashRows);
	}

	private boolean compareHashCols(LinkedHashMap<C, Integer> mappingIndex, LinkedHashMap<C, Integer> originalHash) {

		for (Entry<C, Integer> entry : mappingIndex.entrySet()) {
			if (entry.getValue() != originalHash.get(entry.getKey())) {
				return false;
			}
		}
		return true;
	}

	private boolean compareHashRows(LinkedHashMap<R, Integer> mappingIndex, LinkedHashMap<R, Integer> originalHash) {

		for (Entry<R, Integer> entry : mappingIndex.entrySet()) {
			if (entry.getValue() != originalHash.get(entry.getKey())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Set a element of the dataset.
	 *
	 * @param rowName
	 * @param columnName
	 * @param value
	 */
	public void setElement(R rowName, C columnName, double value) {

		Integer row = hashRows.get(rowName);
		Integer column = hashCols.get(columnName);

		if (row != null && column != null) {
			matrix.setQuick(row, column, value);
		} else if (row == null) {
			throw new NoSuchElementException("Row not found: " + rowName.toString());
		} else {
			throw new NoSuchElementException("Column not found: " + columnName.toString());
		}

	}

	/**
	 * Get specific element.
	 *
	 * @param rowName
	 * @param columnName
	 * @return
	 */
	public double getElement(R rowName, C columnName) {

		Integer row = hashRows.get(rowName);
		Integer column = hashCols.get(columnName);

		if (row != null && column != null) {
			return matrix.getQuick(row, column);
		} else if (row == null) {
			throw new NoSuchElementException("Row not found: " + rowName.toString());
		} else {
			throw new NoSuchElementException("Column not found: " + columnName.toString());
		}
	}

	public DoubleMatrix1D getRow(R rowName) {
		Integer row = hashRows.get(rowName);
		if (row != null) {
			return matrix.viewRow(row);
		} else {
			throw new NoSuchElementException("Row not found: " + rowName.toString());
		}
	}

	public DoubleMatrix1D getRow(int row) {
		return matrix.viewRow(row);
	}

	public DoubleMatrix1D getCol(C colName) {
		Integer col = hashCols.get(colName);
		if (col != null) {
			return matrix.viewColumn(col);
		} else {
			throw new NoSuchElementException("Col not found: " + colName.toString());
		}
	}

	public DoubleMatrix1D getCol(int col) {
		return matrix.viewColumn(col);
	}

	/**
	 * Get specific element.
	 *
	 * @param row
	 * @param column
	 * @return
	 */
	public double getElement(int row, int column) {

		return matrix.get(row, column);
	}

	/**
	 * Get specific element. Fast but no check if query is in range
	 *
	 * @param row
	 * @param column
	 * @return
	 */
	public double getElementQuick(int row, int column) {

		return matrix.getQuick(row, column);
	}

	/**
	 * Set specific element. Fast but no check if query is in range
	 *
	 * @param row
	 * @param column
	 * @param value
	 * @return
	 */
	public void setElementQuick(int row, int column, double value) {

		matrix.setQuick(row, column, value);
	}

	public boolean containsRow(R rowId) {
		return hashRows.containsKey(rowId);
	}

	public boolean containsCol(C colId) {
		return hashCols.containsKey(colId);
	}

	/**
	 * Creates a new view to this dataset with a subset of rows and columns.
	 *
	 * New order of rows and cols is based on input order.
	 *
	 * @param rowsToView
	 * @param colsToView
	 * @return
	 */
	public DoubleMatrixDataset<R, C> viewSelection(LinkedHashSet<R> rowsToView, LinkedHashSet<C> colsToView) {

		int[] rowNrs = new int[rowsToView.size()];
		int[] colNrs = new int[colsToView.size()];

		LinkedHashMap<R, Integer> newHashRows = new LinkedHashMap<>(rowsToView.size());
		LinkedHashMap<C, Integer> newHashCols = new LinkedHashMap<>(colsToView.size());

		int i = 0;
		for (R row : rowsToView) {

			rowNrs[i] = hashRows.get(row);
			newHashRows.put(row, i++);

		}

		i = 0;
		for (C col : colsToView) {

			colNrs[i] = hashCols.get(col);
			newHashCols.put(col, i++);

		}

		return new DoubleMatrixDataset<>(matrix.viewSelection(rowNrs, colNrs), newHashRows, newHashCols);

	}

	/**
	 * Creates a new view to this dataset with a subset of rows.
	 *
	 * New order of rows is based on input order.
	 *
	 * @param rowsToView
	 * @return
	 */
	public DoubleMatrixDataset<R, C> viewRowSelection(LinkedHashSet<R> rowsToView) {

		int[] rowNrs = new int[rowsToView.size()];

		LinkedHashMap<R, Integer> newHashRows = new LinkedHashMap<>(rowsToView.size());

		int i = 0;
		for (R row : rowsToView) {

			//Null pointer below probabli indicates looking for non existing row
			rowNrs[i] = hashRows.get(row);
			newHashRows.put(row, i++);

		}

		return new DoubleMatrixDataset<>(matrix.viewSelection(rowNrs, null), newHashRows, hashCols);

	}

	/**
	 * Creates a new view to this dataset with a subset of rows.
	 *
	 * New order of rows is based on input order.
	 *
	 * @param rowsToView
	 * @return
	 */
	public DoubleMatrixDataset<R, C> viewRowSelection(R[] rowsToView) {

		int[] rowNrs = new int[rowsToView.length];

		LinkedHashMap<R, Integer> newHashRows = new LinkedHashMap<>(rowsToView.length);

		int i = 0;
		for (R row : rowsToView) {

			//Null pointer below probabli indicates looking for non existing row
			rowNrs[i] = hashRows.get(row);
			newHashRows.put(row, i++);

		}

		if (rowsToView.length != newHashRows.size()) {
			throw new RuntimeException("Duplicates in rowsToView");
		}

		return new DoubleMatrixDataset<>(matrix.viewSelection(rowNrs, null), newHashRows, hashCols);

	}

	/**
	 * Creates a new view to this dataset with a subset of cools.
	 *
	 * New order of cols is based on input order.
	 *
	 * @param colsToView
	 * @return
	 */
	public DoubleMatrixDataset<R, C> viewColSelection(C[] colsToView) {

		int[] colNrs = new int[colsToView.length];

		LinkedHashMap<C, Integer> newHashCols = new LinkedHashMap<>(colsToView.length);

		int i = 0;
		for (C col : colsToView) {

			//Null pointer below probabli indicates looking for non existing row
			colNrs[i] = hashCols.get(col);
			newHashCols.put(col, i++);

		}

		if (colsToView.length != newHashCols.size()) {
			throw new RuntimeException("Duplicates in colsToView");
		}

		return new DoubleMatrixDataset<>(matrix.viewSelection(null, colNrs), hashRows, newHashCols);

	}

	/**
	 * Creates a new view to this dataset with a subset of cools.
	 *
	 * New order of cols is based on input order.
	 *
	 * @param colsToView
	 * @return
	 */
	public DoubleMatrixDataset<R, C> viewColSelection(LinkedHashSet<C> colsToView) {

		int[] colNrs = new int[colsToView.size()];

		LinkedHashMap<C, Integer> newHashCols = new LinkedHashMap<>(colsToView.size());

		int i = 0;
		for (C col : colsToView) {

			//Null pointer below probably indicates looking for non existing col
			colNrs[i] = hashCols.get(col);
			newHashCols.put(col, i++);

		}

		return new DoubleMatrixDataset<>(matrix.viewSelection(null, colNrs), hashRows, newHashCols);

	}

	public DoubleMatrix1D viewRow(R row) {
		return matrix.viewRow(hashRows.get(row));
	}

	/**
	 *
	 * @return Correlation matrix on columns
	 */
	public DoubleMatrixDataset<C, C> calculateCorrelationMatrix() {

		DoubleMatrix2D correlationMatrix = DoubleStatistic.correlation(DoubleStatistic.covariance(this.matrix));
		return new DoubleMatrixDataset<>(correlationMatrix, hashCols, hashCols);

	}

	/**
	 *
	 * @return Covariance matrix on columns
	 */
	public DoubleMatrixDataset<C, C> calculateCovarianceMatrix() {

		DoubleMatrix2D covarianceMatrix = DoubleStatistic.covariance(this.matrix);
		return new DoubleMatrixDataset<>(covarianceMatrix, hashCols, hashCols);

	}

	public int getRowIndex(R row) {
		return this.hashRows.get(row);
	}

	public DoubleMatrixDataset<R, C> createRowForceNormalDuplicate() {

		DoubleMatrixDataset<R, C> newDataset = new DoubleMatrixDataset<>(hashRows, hashCols);

		NaturalRanking ranking = new NaturalRanking(NaNStrategy.FAILED,
				TiesStrategy.AVERAGE);

		for (int r = 0; r < matrix.rows(); ++r) {

			double[] row = matrix.viewRow(r).toArray();

			double mean = JSci.maths.ArrayMath.mean(row);
			double stdev = JSci.maths.ArrayMath.standardDeviation(row);

			double[] rankedValues = ranking.rank(row);

			for (int s = 0; s < matrix.columns(); s++) {
				double pValue = (0.5d + rankedValues[s] - 1d) / (double) (rankedValues.length);

				newDataset.setElementQuick(r, s, mean + cern.jet.stat.Probability.normalInverse(pValue) * stdev);
			}

		}

		return newDataset;

	}

}

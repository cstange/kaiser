package cl.kaiser.hana;

import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

/**
 * 
 * @author caco
 *
 */
public class KProcedure {
	
	private static final Logger log		= Logger.getLogger(KProcedure.class.getName());
		
	private String 		 pSchema;
	private String 		 pProcedure;

	private final String  sqlProcedure		= "SELECT PARAMETER_NAME,PARAMETER_TYPE,POSITION,DATA_TYPE_NAME,HAS_DEFAULT_VALUE,TABLE_TYPE_NAME FROM SYS.PROCEDURE_PARAMETERS WHERE SCHEMA_NAME = ? AND PROCEDURE_NAME = ?  ORDER BY POSITION";
	private final String  sqlType			= "SELECT COLUMN_NAME,DATA_TYPE_NAME,COLUMN_SIZE,DECIMAL_DIGITS FROM SYS.TABLE_COLUMNS_ODBC WHERE SCHEMA_NAME = ? AND TABLE_NAME = ? ORDER BY POSITION";
	private final String  table_type		= "TABLE_TYPE";
	private final String  table_temp		= "TEMP_";
	private final boolean sensitiveParms	= false;
	
	private List<KMetaObj> 		parameters;

	final private class KMetaObj {
		public String 				ParameterName;
		public String 				Type;
		public int	 				Position;
		public String 				DataType;
		public boolean 				IsDefault;
		public String 				TableName;
		public List<KMetaTableObj>	TableColumns;	
		public int	 				FixPosition;	
	}
	
	final private class KMetaTableObj {
		public String 				ColumnName;
		public String 				DataType;
		public int	 				Length;
		public int					Scale;
	}	
	/**
	 * 
	 * @param con
	 * @param schema
	 * @param procedureName
	 */
	public KProcedure(String schema,String procedureName) {
		setpSchema(schema);
		setpProcedure(procedureName);
	}
	
	/**
	 * 
	 * @throws KProcedureException
	 */
	public void loadDefinitionFromDatabase(Connection pcon) throws KProcedureException {		
		parameters=new ArrayList<KMetaObj>();	
		log.log(Level.INFO,"Definition {0} loading",getpProcedure());
    	try (PreparedStatement st = pcon.prepareStatement(sqlProcedure);) {
    		st.setString(1, getpSchema());
    		st.setString(2, getpProcedure());
    		try (ResultSet rs=st.executeQuery()) {
    			while (rs!=null && rs.next()){
    				KMetaObj obj=new KMetaObj();
    				parameters.add(obj);
    				obj.ParameterName	= rs.getString(1);
    				obj.Type			= rs.getString(2);
    				obj.Position		= rs.getInt(3);
    				obj.DataType		= rs.getString(4);
    				obj.IsDefault		= rs.getString(5).equals("TRUE")?true:false;
    				obj.TableName		= rs.getString(6);
    				//Table as parameter
    				if (obj.DataType.equals(table_type) && obj.TableName!=null) {    					
    					try (PreparedStatement stype = pcon.prepareStatement(sqlType)) {
    						stype.setString(1, getpSchema());
    						stype.setString(2, obj.TableName);
    						try (ResultSet rtype=stype.executeQuery()) {
    							obj.TableColumns=new ArrayList<KMetaTableObj>();
	    						while (rtype!=null && rtype.next()){
	    							KMetaTableObj typeobj=new KMetaTableObj();
	    							obj.TableColumns.add(typeobj);
	    							typeobj.ColumnName		= rtype.getString(1);	
	    							typeobj.DataType		= rtype.getString(2);
	    							typeobj.Length			= rtype.getInt(3);
	    							typeobj.Scale			= rtype.getInt(4);
	    						}
    						}
    					}
    				}    				    				
    			}
    		}
    		log.log(Level.INFO,"Definition {0} end",getpProcedure());
    	}catch (SQLException er) {
    		log.log(Level.SEVERE,"can't load definition {0} by SQL exception",getpProcedure());
    		throw new KProcedureException("can't load definition by SQL exception",er);
    	}catch (Exception er) {
    		log.log(Level.SEVERE,"can't load definition {0} by unmanaged exception",getpProcedure());
    		throw new KProcedureException("can't load definition by unmanaged exception",er);
    	}
	}

	/**
	 * 
	 * @return
	 */
	public String getJsonDefinition() {
		Gson gson = new Gson();
		return gson.toJson(parameters);		
	}
		
	/**
	 * 
	 * @param json
	 * @return 
	 */
	public void loadDefinitionFromJson(String json) {
		Gson gson = new Gson();
		Type listType = new TypeToken<List<KMetaObj>>() {}.getType();
		parameters=gson.fromJson(json ,listType);
	}
	
	/**
	 * 
	 * @param input
	 * @return
	 * @throws KProcedureException
	 */
	public JsonObject call(Connection pcon,String input) throws KProcedureException {
		JsonParser parser 		= new JsonParser();
        JsonObject dataJsonIn	= parser.parse(input).getAsJsonObject();
        return call(pcon,dataJsonIn);
	}

	/**
	 * 
	 * @param input
	 * @return
	 * @throws KProcedureException
	 * @throws  
	 */
	public JsonObject call(Connection pcon,JsonObject inputJson) throws KProcedureException  {
       
		JsonObject 		 out			= new JsonObject();        
        String 			 sqlCall		= "";
        String 			 callParms		= "";
        List<String>	 tempDropTables	= new ArrayList<String>();
        List<String>	 tempCreaTables	= new ArrayList<String>();
        List<String>	 tempInsTables	= new ArrayList<String>();
        SimpleDateFormat formatterTS 	= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        SimpleDateFormat formatterSD 	= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat formatterDD 	= new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat formatterTT 	= new SimpleDateFormat("HH:mm:ss");
        
        //fix insensitive parameters
        JsonObject inJson=inputJson;
        if (!sensitiveParms) {
        	JsonObject tinJson=new JsonObject();
        	for (Map.Entry<String,JsonElement> entry : inputJson.entrySet()) {
        		tinJson.add(entry.getKey().toUpperCase(), entry.getValue());
        	}
        	inJson=tinJson;
        }
        
        //check if procedure definitions are loaded
        if (parameters==null) loadDefinitionFromDatabase(pcon);

    	//Prepare call
        try {
        	log.log(Level.INFO,"Prepare call {0} start",getpProcedure());
	        int FixPosition=1;
	        for (KMetaObj obj :parameters) {        
	        	//Tables IN, need temporary table 
	        	if (obj.DataType.equals(table_type) && obj.Type.equals("IN")) {
	        		String tempTableName=(table_temp+UUID.randomUUID().toString().replace("-", "")).toUpperCase();
	        		tempDropTables.add(String.format("DROP TABLE #%s", tempTableName));  
	        		String tempcols="";
	        		for (KMetaTableObj col:obj.TableColumns) {
	        			if (col.DataType.equals("CHAR"))        	  tempcols+=String.format("\"%s\" %s(%d),",col.ColumnName,col.DataType,col.Length);
	        			else if (col.DataType.equals("NVARCHAR"))     tempcols+=String.format("\"%s\" %s(%d),",col.ColumnName,col.DataType,col.Length);
	        			else if (col.DataType.equals("VARCHAR"))      tempcols+=String.format("\"%s\" %s(%d),",col.ColumnName,col.DataType,col.Length);
	        			else if (col.DataType.equals("BIGINT"))       tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("INTEGER"))      tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("TINYINT"))      tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("SMALLINT"))     tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("DECIMAL"))  	  tempcols+=String.format("\"%s\" %s(%d,%d),",col.ColumnName,col.DataType,col.Length,col.Scale);
	        			else if (col.DataType.equals("SECONDDATE"))   tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("DATE"))   	  tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("TIMESTAMP"))    tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else if (col.DataType.equals("TIME"))   	  tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType); 
	        			else throw new KProcedureException("TYPE "+col.DataType+ " not implemented");
	        		}
	        		if (tempcols.endsWith(",")) tempcols=tempcols.substring(0, tempcols.length()-1);         		        		
	        		tempCreaTables.add(String.format("CREATE LOCAL TEMPORARY TABLE #%s (%s)", tempTableName,tempcols));
	        		JsonArray dataIN=inJson.getAsJsonArray(obj.ParameterName);
	        		if (dataIN!=null) {
	        			for (int i=0;i<dataIN.size();i++) {
	        				JsonObject data=dataIN.get(i).getAsJsonObject();
	        				String insert="";
	        				for (KMetaTableObj col:obj.TableColumns) {
	        					if (col.DataType.equals("CHAR")) 			insert+=String.format("'%s',",data.get(col.ColumnName).getAsString().replace("'", "''"));	        					
	        					else if (col.DataType.equals("NVARCHAR")) 	insert+=String.format("'%s',",data.get(col.ColumnName).getAsString().replace("'", "''"));	        					
	        					else if (col.DataType.equals("VARCHAR"))	insert+=String.format("'%s',",data.get(col.ColumnName).getAsString().replace("'", "''"));	        					
	        					else if (col.DataType.equals("BIGINT")) 	insert+=String.format("%s,",data.get(col.ColumnName).getAsBigInteger());	        					
	        					else if (col.DataType.equals("INTEGER")) 	insert+=String.format("%s,",data.get(col.ColumnName).getAsInt());	        					
	        					else if (col.DataType.equals("TINYINT")) 	insert+=String.format("%s,",data.get(col.ColumnName).getAsShort());	        					
	        					else if (col.DataType.equals("SMALLINT")) 	insert+=String.format("%s,",data.get(col.ColumnName).getAsShort());	        					
	        					else if (col.DataType.equals("DECIMAL")) 	insert+=String.format("%s,",data.get(col.ColumnName).getAsBigDecimal());	        					
	        					else if (col.DataType.equals("SECONDDATE")) insert+=String.format("'%s',",data.get(col.ColumnName).getAsString());	        					
	        					else if (col.DataType.equals("DATE")) 		insert+=String.format("'%s',",data.get(col.ColumnName).getAsString());	        					
	        					else if (col.DataType.equals("TIMESTAMP")) 	insert+=String.format("'%s',",data.get(col.ColumnName).getAsString());	        					
	        					else if (col.DataType.equals("TIME"))	 	insert+=String.format("'%s',",data.get(col.ColumnName).getAsString());	        					
	        					else throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");	        					
	        				}
	        				if (insert.endsWith(",")) insert=insert.substring(0, insert.length()-1); 
	        				tempInsTables.add(String.format("INSERT INTO #%s values(%s)", tempTableName,insert));        				
	        			}
	        		} 
	        		callParms+=String.format("%s => #%s,",obj.ParameterName,tempTableName);
	        	//Tables OUT
	        	}else if (obj.DataType.equals(table_type) && obj.Type.equals("OUT")) {
	        		callParms+=String.format("%s => ?,",obj.ParameterName);
	        	//Scalar IN // OUT //INOUT
	        	}else {
	        		if (!(obj.IsDefault && inJson.get(obj.ParameterName)==null)) { 
	        			callParms+=String.format("%s => ?,",obj.ParameterName);
	        			obj.FixPosition=FixPosition++;
	        		}
	        	}
	        }
	        if (callParms.endsWith(",")) callParms=callParms.substring(0, callParms.length()-1);
	        log.log(Level.INFO,"Prepare call {0} end",getpProcedure());
	    //end prepare call
    	}catch (Exception er) {
    		throw new KProcedureException("can't prepare call by unmanaged exception",er);
    	}    
        
        //execute call
        try {                
	        //Execute create temporary tables
	        if (tempCreaTables.size()>0) {
	        	log.log(Level.INFO,"Execute call {0} temporary tables create start",getpProcedure());
				try (Statement stmt = pcon.createStatement();) {
					for (String temptable:tempCreaTables) { 
						stmt.addBatch(temptable);
					}
					stmt.executeBatch();
				} 
				log.log(Level.INFO,"Execute call {0} temporary tables create end",getpProcedure());
	        }
			
	        //Execute insert temporary tables
	        if (tempInsTables.size()>0) {
	        	log.log(Level.INFO,"Execute call {0} temporary tables insert start",getpProcedure());
				try (Statement stmt = pcon.createStatement();) {
					for (String temptable:tempInsTables) { 
						stmt.addBatch(temptable);
					}
					stmt.executeBatch();
				} 
				log.log(Level.INFO,"Execute call {0} temporary tables insert end",getpProcedure());
	        }
	        
	    	//Call database procedure
	    	sqlCall=String.format("{call %s.\"%s\" ( %s )} ",getpSchema(),getpProcedure(),callParms);    	
	        log.log(Level.INFO,"Execute call {0} prepareCall={1}",new Object[]{ getpProcedure(), sqlCall }); 
	    	try (CallableStatement  st = pcon.prepareCall(sqlCall)) {				
				for (KMetaObj obj :parameters) { 
					if (!obj.DataType.equals(table_type)) {
						if (obj.Type.equals("IN") || obj.Type.equals("INOUT")) {
							JsonElement elem=inJson.get(obj.ParameterName);
							if (obj.IsDefault && elem==null) continue; 
							if (elem==null) throw new KProcedureException("Parameter "+obj.ParameterName+ " missing");
							if (obj.DataType.equals("CHAR")) {
								st.setString(obj.FixPosition,elem.getAsString());
							}else if (obj.DataType.equals("NVARCHAR")) {
								st.setString(obj.FixPosition, elem.getAsString());	
							}else if (obj.DataType.equals("VARCHAR")) {
								st.setString(obj.FixPosition, elem.getAsString());										
							}else if (obj.DataType.equals("BIGINT")) {
								st.setLong(obj.FixPosition, elem.getAsLong());
							}else if (obj.DataType.equals("INTEGER")) {
								st.setInt(obj.FixPosition, elem.getAsInt());	
							}else if (obj.DataType.equals("TINYINT")) {
								st.setInt(obj.FixPosition, elem.getAsShort());	
							}else if (obj.DataType.equals("SMALLINT")) {
								st.setInt(obj.FixPosition, elem.getAsShort());									
							}else if (obj.DataType.equals("DECIMAL")) {
								st.setBigDecimal(obj.FixPosition, elem.getAsBigDecimal());	
							}else if (obj.DataType.equals("SECONDDATE")) {
								st.setTimestamp(obj.FixPosition, new java.sql.Timestamp(formatterSD.parse(elem.getAsString()).getTime()));							
							}else if (obj.DataType.equals("DATE")) {
								st.setDate(obj.FixPosition, new java.sql.Date(formatterDD.parse(elem.getAsString()).getTime()));							
							}else if (obj.DataType.equals("TIMESTAMP")) {
								st.setTimestamp(obj.FixPosition, new java.sql.Timestamp(formatterTS.parse(elem.getAsString()).getTime()));	
							}else if (obj.DataType.equals("TIME")) {
								st.setTime(obj.FixPosition, new java.sql.Time(formatterTT.parse(elem.getAsString()).getTime()));								
							}else {
								throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
							}		 							
						}
						else { 
							if (obj.DataType.equals("CHAR")) {
								st.registerOutParameter(obj.FixPosition, Types.CHAR);
							}else if (obj.DataType.equals("NVARCHAR")) {
								st.registerOutParameter(obj.FixPosition, Types.NVARCHAR);								
							}else if (obj.DataType.equals("VARCHAR")) {
								st.registerOutParameter(obj.FixPosition, Types.VARCHAR);									
							}else if (obj.DataType.equals("INTEGER")) {
								st.registerOutParameter(obj.FixPosition, Types.INTEGER);
							}else if (obj.DataType.equals("SMALLINT")) {
								st.registerOutParameter(obj.FixPosition, Types.SMALLINT);								
							}else if (obj.DataType.equals("TINYINT")) {
								st.registerOutParameter(obj.FixPosition, Types.TINYINT);								
							}else if (obj.DataType.equals("BIGINT")) {
								st.registerOutParameter(obj.FixPosition, Types.BIGINT);	
							}else if (obj.DataType.equals("DECIMAL")) {
								st.registerOutParameter(obj.FixPosition, Types.DECIMAL);									
							}else if (obj.DataType.equals("TIME")) {
								st.registerOutParameter(obj.FixPosition, Types.TIME);
							}else if (obj.DataType.equals("TIMESTAMP")) {
								st.registerOutParameter(obj.FixPosition, Types.TIMESTAMP);
							}else if (obj.DataType.equals("DATE")) {
								st.registerOutParameter(obj.FixPosition, Types.DATE);
							}else if (obj.DataType.equals("SECONDDATE")) {
								st.registerOutParameter(obj.FixPosition, Types.TIMESTAMP);
							}else {
								throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
							}
						}
					}				
				}
				st.executeUpdate();		
				//Read Scalar out
				log.log(Level.INFO,"Execute call {0} read out parameter scalar",getpProcedure()); 
				for (KMetaObj obj :parameters) { 						
					if (!obj.DataType.equals(table_type)) {
						if (obj.Type.equals("OUT") || obj.Type.equals("INOUT")) {
							if (obj.DataType.equals("CHAR")) {
								out.addProperty(obj.ParameterName,st.getString(obj.FixPosition));
							}else if (obj.DataType.equals("NVARCHAR")) {
								out.addProperty(obj.ParameterName,st.getString(obj.FixPosition));	
							}else if (obj.DataType.equals("VARCHAR")) {
								out.addProperty(obj.ParameterName,st.getString(obj.FixPosition));										
							}else if (obj.DataType.equals("INTEGER")) {
								out.addProperty(obj.ParameterName,st.getInt(obj.FixPosition));
							}else if (obj.DataType.equals("SMALLINT")) {
								out.addProperty(obj.ParameterName,st.getShort(obj.FixPosition));
							}else if (obj.DataType.equals("TINYINT")) {
								out.addProperty(obj.ParameterName,st.getShort(obj.FixPosition));								
							}else if (obj.DataType.equals("BIGINT")) {
								out.addProperty(obj.ParameterName,st.getLong(obj.FixPosition));	
							}else if (obj.DataType.equals("DECIMAL")) {
								out.addProperty(obj.ParameterName,st.getBigDecimal(obj.FixPosition));	
							}else if (obj.DataType.equals("TIME")) {
								out.addProperty(obj.ParameterName, formatterTT.format(st.getTime(obj.FixPosition))  );									
							}else if (obj.DataType.equals("TIMESTAMP")) {
								out.addProperty(obj.ParameterName, formatterTS.format(st.getTimestamp(obj.FixPosition))  );									
							}else if (obj.DataType.equals("DATE")) {
								out.addProperty(obj.ParameterName, formatterDD.format(st.getDate(obj.FixPosition))  );	
							}else if (obj.DataType.equals("SECONDDATE")) {
								out.addProperty(obj.ParameterName, formatterSD.format(st.getTimestamp(obj.FixPosition))  );									
							}else {
								throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
							}
						}
					}
				}
				//Read Tables out
				log.log(Level.INFO,"Execute call {0} read out parameter tables start",getpProcedure()); 
				do {
					int resultCount=0;
					try (ResultSet rs = st.getResultSet();) {
						if (rs==null) break;
						ResultSetMetaData mt = rs.getMetaData();
						String propName="ResultSet_"+resultCount++;
						for (KMetaObj obj :parameters) { 
							if (obj.DataType.equals(table_type)) {
								if (obj.Type.equals("OUT") || obj.Type.equals("INOUT")) {
									if (mt.getTableName(1).startsWith(obj.ParameterName)) {
										propName=obj.ParameterName;
										break;
									}
								}
							}
						}
						JsonArray jdata=new JsonArray();
						out.add(propName, jdata);						
						int numberOfColumns = mt.getColumnCount();
						while (rs!=null && rs.next()) {
							JsonObject jobj = new JsonObject();
							for (int i = 1; i <= numberOfColumns; i++) {	
								String ctype=mt.getColumnTypeName(i);
								if (ctype.equals("CHAR")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getString(i));
								} else if (ctype.equals("NVARCHAR")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getString(i));
								} else if (ctype.equals("VARCHAR")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getString(i));
								}else if (ctype.equals("INTEGER")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getInt(i));
								}else if (ctype.equals("SMALLINT")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getShort(i));
								}else if (ctype.equals("TINYINT")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getShort(i));
								}else if (ctype.equals("BIGINT")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getLong(i));								
								}else if (ctype.equals("DECIMAL")) {
									jobj.addProperty(mt.getColumnLabel(i),rs.getBigDecimal(i));
								}else if (ctype.equals("TIME")) {  
									jobj.addProperty(mt.getColumnLabel(i),formatterTT.format(rs.getTime(i)));								
								}else if (ctype.equals("TIMESTAMP")) {  
									jobj.addProperty(mt.getColumnLabel(i),formatterTS.format(rs.getTimestamp(i)));								
								}else if (ctype.equals("DATE")) {  
									jobj.addProperty(mt.getColumnLabel(i),formatterDD.format(rs.getDate(i)));								
								}else if (ctype.equals("SECONDDATE")) {  
									String xx=formatterSD.format(rs.getTimestamp(i));
									jobj.addProperty(mt.getColumnLabel(i),xx);								
								}else {
									throw new KProcedureException("TYPE "+ctype+ " not implemented");
								}
							}
							jdata.add(jobj);						
						}
					}
				} while (st.getMoreResults()); 
				log.log(Level.INFO,"Execute call {0} read out parameter tables end",getpProcedure()); 		   
			}
		//end execute call
        }catch (SQLException er) {
        	log.log(Level.SEVERE,"can't execute call "+getpProcedure()+" by SQL exception");
    		throw new KProcedureException("can't execute call by SQL exception",er);
        }catch (ParseException er) {
        	log.log(Level.SEVERE,"can't execute call "+getpProcedure()+" by Parse exception");
    		throw new KProcedureException("can't execute call by Parse exception",er);      
        }finally {
        	 //Remove temporary tables
        	try {
	        	if (tempDropTables.size()>0) {
					try (Statement stmt = pcon.createStatement();) {
						for (String droptable:tempDropTables) {
							stmt.addBatch(droptable);
						}
						stmt.executeBatch();
					}
	        	}
        	}catch (Exception ex) {
        		log.log(Level.INFO,"Can´t delete temporary tables: "+ex.toString());
        	}
		}   
		return out;		
	}

	/**
	 * 
	 * @return
	 */
	public String getpSchema() {
		return pSchema;
	}

	/**
	 * 
	 * @param pSchema
	 */
	public void setpSchema(String pSchema) {
		this.pSchema = pSchema;
	}

	/**
	 * 
	 * @return
	 */
	public String getpProcedure() {
		return pProcedure;
	}

	/**
	 * 
	 * @param pProcedure
	 */
	public void setpProcedure(String pProcedure) {
		this.pProcedure = pProcedure;
	}

}

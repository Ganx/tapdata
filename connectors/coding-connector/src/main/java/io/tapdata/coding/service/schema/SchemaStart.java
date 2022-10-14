package io.tapdata.coding.service.schema;

import io.tapdata.coding.CodingConnector;
import io.tapdata.coding.utils.tool.Checker;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import org.reflections.Reflections;

import java.util.*;

public interface SchemaStart {
    static final String TAG = SchemaStart.class.getSimpleName();
    Set<Class<? extends SchemaStart>> schemaSet = new HashSet<>();
    public Boolean use();
    public String tableName();

    public TapTable document(TapConnectionContext connectionContext);

    public TapTable csv(TapConnectionContext connectionContext);

    public Map<String,Object> autoSchema(Map<String,Object> eventData);

    public static SchemaStart getSchemaByName(String schemaName){
        if (Checker.isEmpty(schemaName)) return null;
        Class clz = null;
        try {
            clz = Class.forName("io.tapdata.coding.service.schema" + "."+schemaName);
            return ((SchemaStart)clz.newInstance());
        } catch (ClassNotFoundException e) {
            TapLogger.debug(TAG, "ClassNotFoundException for Schema {}",schemaName);
        } catch (InstantiationException e1) {
            TapLogger.debug(TAG, "InstantiationException for Schema {}",schemaName);
        } catch (IllegalAccessException e2) {
            TapLogger.debug(TAG, "IllegalAccessException for Schema {}",schemaName);
        }
        return null;
    }

    public static List<SchemaStart> getAllSchemas(){
        //Reflections reflections = new Reflections("io.tapdata.coding.service.schema");//SchemaStart.class.getPackage().getName()
        //Set<Class<? extends SchemaStart>> allImplClass = reflections.getSubTypesOf(SchemaStart.class);
        Set<Class<? extends SchemaStart>> allImplClass = new HashSet<Class<? extends SchemaStart>>(){{
            add(Issues.class);
            add(Iterations.class);
            add(ProjectMembers.class);
        }};
        List<SchemaStart> schemaList = new ArrayList<>();
        allImplClass.forEach(schemaClass->{
            SchemaStart schema = null;
            try {
                schema = schemaClass.newInstance();
                if (schema.use()){
                    schemaList.add(schema);
                }
            } catch (InstantiationException e) {
                TapLogger.debug(TAG, "InstantiationException for Schema.");
            } catch (IllegalAccessException e) {
                TapLogger.debug(TAG,"IllegalAccessException for Schema.");
            }
        });
        return schemaList;
    }

    public default Map<String,Object> eventMapToSchemaMap(Map<String,Object> eventData,Map<String,Object> kvMap){
            Map<String,Object> map = new HashMap<>();
            kvMap.forEach((k,v)->{
                String subKey = (String)v;
                String[] subKeys = subKey.split("\\.");
                Object subValue = null;
                if (subKeys.length>0) {
                    subValue = eventData.get(subKeys[0]);
                    for (int i = 1; subKeys.length > 1 && i < subKeys.length && Checker.isNotEmpty(subValue); i++) {
                        subValue = ( (Map<String,Object>)subValue).get(subKeys[i]);
                    }
                }
                if (Checker.isNotEmpty(subValue)) {
                    map.put(k, subValue);
                }
            });
            return map;
        }
}

package org.javers.repository.sql.schema;

import org.javers.repository.sql.ConnectionProvider;
import org.polyjdbc.core.PolyJDBC;
import org.polyjdbc.core.dialect.*;
import org.polyjdbc.core.schema.SchemaInspector;
import org.polyjdbc.core.schema.SchemaManager;
import org.polyjdbc.core.schema.model.Schema;
import org.polyjdbc.core.util.TheCloser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;

/**
 * @author bartosz walacik
 */
public class JaversSchemaManager {
    private static final Logger logger = LoggerFactory.getLogger(JaversSchemaManager.class);

    private SchemaInspector schemaInspector;
    private SchemaManager schemaManager;
    private final Dialect dialect;
    private final FixedSchemaFactory schemaFactory;
    private final PolyJDBC polyJDBC;
    private final ConnectionProvider connectionProvider;

    public JaversSchemaManager(Dialect dialect, FixedSchemaFactory schemaFactory, PolyJDBC polyJDBC, ConnectionProvider connectionProvider) {
        this.dialect = dialect;
        this.schemaFactory = schemaFactory;
        this.polyJDBC = polyJDBC;
        this.connectionProvider = connectionProvider;
    }

    public void ensureSchema() {
        this.schemaInspector = polyJDBC.schemaInspector();
        this.schemaManager = polyJDBC.schemaManager();

        for (Map.Entry<String, Schema> e : schemaFactory.allTablesSchema(dialect).entrySet()){
            ensureTable(e.getKey(), e.getValue());
        }

        alterCommitIdColumnIfNeeded();

        TheCloser.close(schemaManager, schemaInspector);
    }

    /**
     * JaVers 1.3.15 to 1.3.16 schema migration
     */
    private void alterCommitIdColumnIfNeeded() {

        if (getTypeOf("jv_commit", "commit_id") == Types.VARCHAR){
            logger.info("migrating db schema from JaVers 1.3.15 to 1.3.16 ...");

            if (dialect instanceof PostgresDialect){
                executeSQL("ALTER TABLE jv_commit ALTER COLUMN commit_id TYPE numeric(12,2) USING commit_id::numeric");
            } else if (dialect instanceof H2Dialect){
                executeSQL("ALTER TABLE jv_commit ALTER COLUMN commit_id numeric(12,2)");
            } else if (dialect instanceof MysqlDialect){
                executeSQL("ALTER TABLE jv_commit MODIFY commit_id numeric(12,2)");
            } else if (dialect instanceof OracleDialect){
                executeSQL("ALTER TABLE jv_commit MODIFY commit_id number(12,2)");
            } else if (dialect instanceof MsSqlDialect) {
                executeSQL("drop index jv_commit_commit_id_idx on jv_commit");
                executeSQL("ALTER TABLE jv_commit ALTER COLUMN commit_id numeric(12,2)");
                executeSQL("CREATE INDEX jv_commit_commit_id_idx ON jv_commit (commit_id)");
            } else {
                logger.error("\nno DB schema migration script for {} :(\nplease contact with JaVers team, javers@javers.org",
                        dialect.getCode());
            }
        }
    }

    private boolean executeSQL(String sql){
        try {
            Statement stmt = connectionProvider.getConnection().createStatement();

            boolean b = stmt.execute(sql);
            stmt.close();

            return b;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int getTypeOf(String tableName, String colName) {
        try {
            Statement stmt = connectionProvider.getConnection().createStatement();

            ResultSet res = stmt.executeQuery("select " + colName + " from " + tableName + " where 1<0");
            int colType = res.getMetaData().getColumnType(1);

            stmt.close();
            res.close();

            return colType;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureTable(String tableName, Schema schema){
        if (schemaInspector.relationExists(tableName)) {
            return;
        }
        logger.info("creating javers table {} ...", tableName);
        schemaManager.create(schema);
    }

    public void dropSchema(){
        throw new RuntimeException("not implemented");
    }
}

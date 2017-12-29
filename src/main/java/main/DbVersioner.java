package main;

import dbHandler.ScriptDeployer;
import dbHandler.ScriptDeploymentException;
import filesHandler.FilesHandler;
import filesHandler.SchemaUpdateFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import versions.Version;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.InvalidPropertiesFormatException;
import java.util.PriorityQueue;

public class DbVersioner {
    private final static Logger logger = LogManager.getLogger("DbVersioner");

    public static void main(String[] args) {
        initializeProperties(args);
        ScriptDeployer scriptDeployer = new ScriptDeployer();
        Connection connection = null;
        try {
            connection = scriptDeployer.initializeDbConnection();
        } catch (ScriptDeploymentException e) {
            handleError("Couldn't connect and/or create requested DB! See causing exception: ", e);
        }
        FilesHandler filesHandler = null;
        try {
            filesHandler = new FilesHandler();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Version version = null;
        try {
            version = scriptDeployer.getDbCurrentVersion(connection);
        } catch (SQLException e) {
            handleError("Could not verify current DB version! Does it have a versions.versions table? See causing exception: ", e);
        }

        try {
            scriptDeployer.cleanViewsAndStoredProcedures(connection);
        } catch (SQLException e) {
            handleError("Could not clean views and functions! See causing exception: ", e);
        }

        if (Properties.installSchemas()) {
            logger.info("Installing schemas, up to version {}", Properties.installUpToVersion());
            try {
                PriorityQueue<SchemaUpdateFile> updateFiles = filesHandler.getSchemaUpdateFiles();
                version = scriptDeployer.updateSchemasUpToVersion(connection, updateFiles, Properties.installUpToVersion());
            } catch (SQLException | IOException e) {
                handleError("Could not install schemas! See causing exception: ", e);
            }
        }

        if (Properties.installViews()) {
            logger.info("Installing views...");
            try {
                scriptDeployer.executeScripts(connection, filesHandler.getViews());
            } catch (SQLException | IOException e) {
                handleError("Could not install views! See causing exception: ", e);
            }
        }

        if (Properties.installStoredProcedures()) {
            logger.info("Installing stored procedures...");
            try {
                scriptDeployer.executeScripts(connection, filesHandler.getStoredProcedures());
            } catch (SQLException | IOException e) {
                handleError("Could not install stored procedures! See causing exception: ", e);
            }
        }

        logger.info("Installation successfully completed! The DB is now version: [{}]", version);
    }

    private static void initializeProperties(String[] args) {
        try {
            PropertiesFlagParser.parseProperties(args);
        } catch (InvalidPropertiesFormatException e) {
            handleError("Invalid flags supplied at startup! See causing exception: ", e);
        }
    }

    private static void handleError(String message, Throwable e) {
        logger.error(message, e);
        System.exit(-1);
    }
}

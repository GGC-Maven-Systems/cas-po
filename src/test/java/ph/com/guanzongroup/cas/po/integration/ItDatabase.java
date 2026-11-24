package ph.com.guanzongroup.cas.po.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.MiscUtil;
import org.junit.Assume;

/**
 * Connection for the integration tests: the same setup as
 * PurchaseOrderGeneralApp, but from a configuration folder given with
 * {@code -Dpo.it.config=<folder>} (a folder like D:/GGC_Maven_Systems whose
 * config/ points to a TEST database). Without it the integration tests are
 * skipped, so they never run against the normal configuration by accident.
 */
final class ItDatabase {

    static final String CONFIG = "po.it.config";

    private static GRiderCAS poApp;

    private ItDatabase() {
    }

    static synchronized GRiderCAS connect() {
        String lsConfig = System.getProperty(CONFIG, "").trim();
        Assume.assumeTrue("Integration tests skipped: run with -D" + CONFIG + "=<test configuration folder>.",
                !lsConfig.isEmpty());
        if (poApp != null) {
            return poApp;
        }

        System.setProperty("sys.default.path.config", lsConfig);
        System.setProperty("sys.default.path.metadata", lsConfig + "/config/metadata/new/");
        System.setProperty("sys.default.path.temp", System.getProperty("po.it.temp", System.getProperty("java.io.tmpdir")));
        loadProperties(lsConfig);

        poApp = MiscUtil.Connect();
        assertNotNull("Unable to connect with the configuration in " + lsConfig, poApp);
        System.out.println("Integration tests connected: branch " + poApp.getBranchCode() + ", user " + poApp.getUserID());
        return poApp;
    }

    /** The cas.properties settings integsys' Login reads. */
    private static void loadProperties(String fsConfig) {
        Properties loProps = new Properties();
        try (InputStream loFile = new FileInputStream(fsConfig + "/config/cas.properties")) {
            loProps.load(loFile);
        } catch (IOException ex) {
            fail("Unable to read " + fsConfig + "/config/cas.properties: " + ex.getMessage());
        }
        for (String lsKey : loProps.stringPropertyNames()) {
            if (System.getProperty(lsKey) == null) {
                System.setProperty(lsKey, loProps.getProperty(lsKey));
            }
        }
        System.setProperty("sys.default.client.token", fsConfig + "/client.token");
        System.setProperty("sys.default.access.token", fsConfig + "/access.token");
    }

    /** First value of a column, or null. */
    static String scalar(GRiderCAS foApp, String fsSQL, String fsColumn) throws SQLException {
        ResultSet loRS = foApp.executeQuery(fsSQL);
        if (loRS == null) {
            return null;
        }
        try {
            return loRS.next() ? loRS.getString(fsColumn) : null;
        } finally {
            MiscUtil.close(loRS);
        }
    }

    /** A setting: -D property, else the default. */
    static String setting(String fsKey, String fsDefault) {
        String lsValue = System.getProperty(fsKey);
        return lsValue == null || lsValue.trim().isEmpty() ? fsDefault : lsValue.trim();
    }
}

package ph.com.guanzongroup.cas.po.model;

import java.sql.SQLException;
import java.util.Date;
import org.guanzon.appdriver.agent.services.Model;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.json.simple.JSONObject;

public class Model_Send_Mail_Master extends Model {

    @Override
    public void initialize() {
        try {
            poEntity = MiscUtil.xml2ResultSet(System.getProperty("sys.default.path.metadata") + XML, getTable());

            poEntity.last();
            poEntity.moveToInsertRow();

            MiscUtil.initRowSet(poEntity);

            //assign default values
            poEntity.updateString("cStatusxx", "0");
            poEntity.updateNull("dPostedxx");
            //end - assign default values

            poEntity.insertRow();
            poEntity.moveToCurrentRow();

            poEntity.absolute(1);

            ID = poEntity.getMetaData().getColumnLabel(1);

            pnEditMode = EditMode.UNKNOWN;
        } catch (SQLException e) {
            logwrapr.severe(e.getMessage());
            System.exit(1);
        }
    }

    public JSONObject setTransactionNo(String transactionNo) {
        return setValue("sTransNox", transactionNo);
    }

    public String getTransactionNo() {
        return (String) getValue("sTransNox");
    }

    public JSONObject setTransactionDate(Date transactionDate) {
        return setValue("dTransact", transactionDate);
    }

    public Date getTransactionDate() {
        return (Date) getValue("dTransact");
    }

    public JSONObject setMailFrom(String mailFrom) {
        return setValue("sMailFrom", mailFrom);
    }

    public String getMailFrom() {
        return (String) getValue("sMailFrom");
    }

    public JSONObject setMailTo(String mailTo) {
        return setValue("sMailToxx", mailTo);
    }

    public String getMailTo() {
        return (String) getValue("sMailToxx");
    }

    public JSONObject setMailCC(String mailCC) {
        return setValue("sMailCCxx", mailCC);
    }

    public String getMailCC() {
        return (String) getValue("sMailCCxx");
    }

    public JSONObject setMailBCC(String mailBCC) {
        return setValue("sMailBCCx", mailBCC);
    }

    public String getMailBCC() {
        return (String) getValue("sMailBCCx");
    }

    public JSONObject setSubject(String subject) {
        return setValue("sSubjectx", subject);
    }

    public String getSubject() {
        return (String) getValue("sSubjectx");
    }

    public JSONObject setMailBody(String mailBody) {
        return setValue("sMailBody", mailBody);
    }

    public String getMailBody() {
        return (String) getValue("sMailBody");
    }

    public JSONObject setAttachment(String attachment) {
        return setValue("sAttached", attachment);
    }

    public String getAttachment() {
        return (String) getValue("sAttached");
    }

    public JSONObject setSourceCode(String sourceCode) {
        return setValue("sSourceCD", sourceCode);
    }

    public String getSourceCode() {
        return (String) getValue("sSourceCD");
    }

    public JSONObject setSourceNo(String sourceNo) {
        return setValue("sSourceNo", sourceNo);
    }

    public String getSourceNo() {
        return (String) getValue("sSourceNo");
    }

    public JSONObject setTransactionStatus(String transactionStatus) {
        return setValue("cStatusxx", transactionStatus);
    }

    public String getTransactionStatus() {
        return (String) getValue("cStatusxx");
    }

    public JSONObject setPostedDate(Date postedDate) {
        return setValue("dPostedxx", postedDate);
    }

    public Date getPostedDate() {
        return (Date) getValue("dPostedxx");
    }

    public JSONObject setModifyingId(String modifyingId) {
        return setValue("sModified", modifyingId);
    }

    public String getModifyingId() {
        return (String) getValue("sModified");
    }

    public JSONObject setModifiedDate(Date modifiedDate) {
        return setValue("dModified", modifiedDate);
    }

    public Date getModifiedDate() {
        return (Date) getValue("dModified");
    }

    @Override
    public String getNextCode() {
        //true = year-based transaction number
        return MiscUtil.getNextCode(getTable(), ID, true, poGRider.getGConnection().getConnection(), poGRider.getBranchCode());
    }
}

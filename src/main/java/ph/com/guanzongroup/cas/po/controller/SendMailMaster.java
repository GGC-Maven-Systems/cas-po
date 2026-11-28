package ph.com.guanzongroup.cas.po.controller;

import java.sql.SQLException;
import org.guanzon.appdriver.agent.ShowDialogFX;
import org.guanzon.appdriver.agent.services.Parameter;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.model.Model_Send_Mail_Master;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderModels;

public class SendMailMaster extends Parameter {

    Model_Send_Mail_Master poModel;

    @Override
    public void initialize() throws SQLException, GuanzonException {
        //status filter used by searchRecord; "0" is the default status of a new record
        psRecdStat = "0";

        poModel = new PurchaseOrderModels(poGRider).SendMailMaster();

        super.initialize();
    }

    @Override
    public JSONObject isEntryOkay() throws SQLException {
        poJSON = new JSONObject();

        //no user level check: the record is saved by whoever approves the source document

        if (isBlank(poModel.getTransactionNo())) {
            return error("Transaction number must not be empty.");
        }

        if (isBlank(poModel.getMailFrom())) {
            return error("Sender must not be empty.");
        }

        if (isBlank(poModel.getMailTo())) {
            return error("Recipient must not be empty.");
        }

        if (isBlank(poModel.getSubject())) {
            return error("Subject must not be empty.");
        }

        if (poModel.getTransactionDate() == null) {
            poModel.setTransactionDate(poGRider.getServerDate());
        }

        //sModified is only 10 characters, so the user id is saved unencrypted
        poModel.setModifyingId(poGRider.getUserID());
        poModel.setModifiedDate(poGRider.getServerDate());

        poJSON.put("result", "success");
        return poJSON;
    }

    @Override
    public Model_Send_Mail_Master getModel() {
        return poModel;
    }

    @Override
    protected void saveComplete() {
        //no cached references to refresh for this record
    }

    @Override
    public JSONObject searchRecord(String value, boolean byCode) throws SQLException, GuanzonException {
        String lsCondition = "";

        if (psRecdStat.length() > 1) {
            for (int lnCtr = 0; lnCtr <= psRecdStat.length() - 1; lnCtr++) {
                lsCondition += ", " + SQLUtil.toSQL(Character.toString(psRecdStat.charAt(lnCtr)));
            }

            lsCondition = "cStatusxx IN (" + lsCondition.substring(2) + ")";
        } else {
            lsCondition = "cStatusxx = " + SQLUtil.toSQL(psRecdStat);
        }

        String lsSQL = MiscUtil.addCondition(getSQ_Browse(), lsCondition);

        poJSON = ShowDialogFX.Search(poGRider,
                lsSQL,
                value,
                "Transaction No.»Date»Recipient»Subject",
                "sTransNox»dTransact»sMailToxx»sSubjectx",
                "sTransNox»dTransact»sMailToxx»sSubjectx",
                byCode ? 0 : 3);

        if (poJSON != null) {
            return poModel.openRecord((String) poJSON.get("sTransNox"));
        } else {
            return error("No record loaded.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private JSONObject error(String message) {
        poJSON = new JSONObject();
        poJSON.put("result", "error");
        poJSON.put("message", message);
        return poJSON;
    }
}
package com.engine.kq.cmd.balanceofleavedetail;

import com.api.customization.qc2757481.KqCustomUtil;
import com.cloudstore.dev.api.util.Util_TableMap;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.biz.KQLeaveRulesComInfo;
import weaver.general.BaseBean;
import weaver.general.PageIdConst;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 加班生成调休的明细
 */
public class GetSearchListCmd extends AbstractCommonCommand<Map<String, Object>> {

    public GetSearchListCmd(Map<String, Object> params, User user) {
        this.user = user;
        this.params = params;
    }

    @Override
    public BizLogContext getLogContext() {
        return null;
    }

    @Override
    public Map<String, Object> execute(CommandContext commandContext) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        try {
            /*人员ID*/
            String resourceId = Util.null2String(params.get("resourceId"));
            /*搜索条件中的年份*/
            String searchYearSelect = Util.null2String(params.get("searchYearSelect"));//日期控件的选择值
            String searchYear = Util.null2String(params.get("searchYear"));//年份
            if (searchYearSelect.equals("5") || searchYearSelect.equals("8")) {
                searchYear = TimeUtil.getDateByOption(searchYearSelect, "0").substring(0, 4);
            }

            //年月
            String monthDaysFirst = "";
            String monthDaysEnd = "";
            Boolean isYearMonth = false;
            KqCustomUtil kqCustomUtil = new KqCustomUtil();
            if (searchYearSelect.equals("9")){
                //年月
                isYearMonth = true;
                searchYear =  Util.null2String(params.get("searchYearMonth"));
                List<String> monthDays = kqCustomUtil.getMonthDays(searchYear);
                for (int i = 0; i < monthDays.size(); i++) {
                    if (i == 0){
                        monthDaysFirst = monthDays.get(i);
                    }
                    if (i == 1){
                        monthDaysEnd = monthDays.get(i);
                    }
                }
            }
            //假期类型的ID
            String leaveRulesId = Util.null2String(params.get("ruleId"));

            if (resourceId.equals("") || searchYear.equals("") || leaveRulesId.equals("")) {
                resultMap.put("status", "-1");
                resultMap.put("message", SystemEnv.getHtmlLabelName(388858, user.getLanguage()));//参数有误
                return resultMap;
            }
            boolean isTiaoXiu = KQLeaveRulesBiz.isTiaoXiu(leaveRulesId);

            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //调休的单位
            String unitName = rulesComInfo.getUnitName(leaveRulesId, user.getLanguage());

            /**********************************************************************************************************/

            boolean canEdit = HrmUserVarify.checkUserRight("KQLeaveRulesEdit:Edit", user);//是否具有编辑权限
            boolean canDelete = HrmUserVarify.checkUserRight("KQLeaveRulesDelete:Delete", user);//是否具有删除权限
            boolean canLog = HrmUserVarify.checkUserRight("KQLeaveRules:Log", user);//是否具有查看日志的权限

            /**********************************************************************************************************/

            String backFields = " b.id,b.resourceId,a.lastName,b.belongYear,b.belongMonth,b.overtimeType,b.baseAmount,b.extraAmount,b.usedAmount,0 as invalidAmount,0 as restAmount,0 as workDayRestAmount,0 as restDayRestAmount,0 as holidayRestAmount,b.effectiveDate,b.expirationDate,b.tiaoxiuamount ";
            String sqlFrom = " from HrmResource a,KQ_BalanceOfLeave b ";
            String sqlWhere = " where 1=1 and a.id=b.resourceId and (isDelete is null or isDelete<>1) ";
            String orderBy = " b.belongYear asc,b.expirationDate asc,b.id asc ";

            if (!resourceId.equals("")) {
                sqlWhere += " and a.id in (" + resourceId + ")";
            }
            if (!searchYear.equals("") && !searchYearSelect.equals("9")) {
                sqlWhere += " and b.belongYear='" + searchYear + "' ";
            }
            if (!leaveRulesId.equals("")) {
                sqlWhere += " and b.leaveRulesId=" + leaveRulesId;
            }
            if (!"".equals(monthDaysFirst) && !"".equals(monthDaysEnd)){
                sqlWhere += " and b.effectiveDate between '"+monthDaysFirst+"' and '"+monthDaysEnd+"' ";
            }

            new BaseBean().writeLog("明细sql" + backFields+sqlFrom+sqlWhere );

            String pageUid = "b29847fa-6877-f7bd-f12b-5d3a35183b25";
            String operateString = "";
            operateString = "<operates width=\"20%\">";
            operateString += "<popedom transmethod=\"weaver.hrm.common.SplitPageTagOperate.getBasicOperate\" otherpara=\"" + canEdit + ":" + canDelete + ":" + canLog + "\"></popedom> ";
            operateString += "  <operate href=\"javascript:openDialog();\" text=\"" + SystemEnv.getHtmlLabelName(93, user.getLanguage()) + "\" index=\"0\"/>";
            operateString += "  <operate href=\"javascript:doDel()\"   text=\"" + SystemEnv.getHtmlLabelName(91, user.getLanguage()) + "\"  index=\"1\"/>";
            operateString += "  <operate href=\"javascript:onLog()\"   text=\"" + SystemEnv.getHtmlLabelName(83, user.getLanguage()) + "\"  index=\"2\"/>";
            operateString += "</operates>";
            String tiaoxiuColString = "";
            if(isTiaoXiu){
                tiaoxiuColString ="       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(126739, user.getLanguage()) + "\" column=\"tiaoxiuamount\" transmethod=\"com.engine.kq.util.KQTransMethod.getTiaoxiuamountShow\" />";
                tiaoxiuColString+="       <col width=\"10%\" text=\"" + "平日剩余余额" + unitName + "\" column=\"workDayRestAmount\" transmethod=\"com.api.customization.qc2757481.KqCustomUtil.getRestAmountShow\" otherpara=\"column:baseAmount+column:extraAmount+column:usedAmount+column:expirationDate+column:tiaoxiuamount+2+column:effectiveDate+column:resourceId\"/>";
                tiaoxiuColString+="       <col width=\"10%\" text=\"" + "休息日剩余余额" + unitName + "\" column=\"restDayRestAmount\" transmethod=\"com.api.customization.qc2757481.KqCustomUtil.getRestAmountShow\" otherpara=\"column:baseAmount+column:extraAmount+column:usedAmount+column:expirationDate+column:tiaoxiuamount+3+column:effectiveDate+column:resourceId\"/>";
                tiaoxiuColString+="       <col width=\"10%\" text=\"" + "法定剩余余额" + unitName + "\" column=\"holidayRestAmount\" transmethod=\"com.api.customization.qc2757481.KqCustomUtil.getRestAmountShow\" otherpara=\"column:baseAmount+column:extraAmount+column:usedAmount+column:expirationDate+column:tiaoxiuamount+1+column:effectiveDate+column:resourceId\"/>";

            }
            String tableString = "" +
                    "<table pageId=\"KQ:BalanceDetailList\" pageUid=\"" + pageUid + "\" tabletype=\"checkbox\" pagesize=\"" + PageIdConst.getPageSize("KQ:BalanceDetailList", user.getUID(), PageIdConst.HRM) + "\" >" +
                    "<sql backfields=\"" + backFields + "\" sqlform=\"" + sqlFrom + "\" sqlwhere=\"" + Util.toHtmlForSplitPage(sqlWhere) + "\"  sqlorderby=\"" + orderBy + "\"  sqlprimarykey=\"id\" sqlsortway=\"asc\" sqlisdistinct=\"false\"/>"
                    + operateString +
                    "   <head>" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(413, user.getLanguage()) + "\" column=\"lastname\" />" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(15933, user.getLanguage()) + "\" column=\"belongYear\"/>" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(887, user.getLanguage()) + "\" column=\"belongMonth\"/>" +
                    //"       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(510282, user.getLanguage()) + "\" column=\"overtimeType\" transmethod=\"com.engine.kq.util.KQTransMethod.getOvertimeTypeShow\" otherpara=\"" + user.getLanguage() + "\"/>" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(130286, user.getLanguage()) + unitName + "\" column=\"baseAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getTotalAmountShow\" otherpara=\"column:extraAmount\"/>" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(26642, user.getLanguage()) + unitName + "\" column=\"usedAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\"/>" +
                    "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389611, user.getLanguage()) + unitName + "\" column=\"invalidAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getInvalidAmountShow\" otherpara=\"column:baseAmount+column:extraAmount+column:usedAmount+column:expirationDate+column:tiaoxiuamount\" />" +
                tiaoxiuColString+
/*
                "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(25723, user.getLanguage()) + unitName + "\" column=\"restAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getRestAmountShow\" otherpara=\"column:baseAmount+column:extraAmount+column:usedAmount+column:expirationDate+column:tiaoxiuamount\"/>" +
*/
                "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(508169, user.getLanguage()) + "\" column=\"effectiveDate\"/>" +
                "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(19547, user.getLanguage()) + "\" column=\"expirationDate\" transmethod=\"com.engine.kq.util.KQTransMethod.getExpirationDateShow\" otherpara=\"" + user.getLanguage() + "\"/>" +
                    "   </head>" +
                    "</table>";
            String sessionKey = pageUid + "_" + Util.getEncrypt(Util.getRandom());
            Util_TableMap.setVal(sessionKey, tableString);
            resultMap.put("sessionkey", sessionKey);
        } catch (Exception e) {
            writeLog(e);
            resultMap.put("status", "-1");
            resultMap.put("message", e.getMessage());
        }
        return resultMap;
    }
}

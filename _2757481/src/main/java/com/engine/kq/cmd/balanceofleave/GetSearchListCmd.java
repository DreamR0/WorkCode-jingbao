package com.engine.kq.cmd.balanceofleave;

import com.alibaba.fastjson.JSON;
import com.api.customization.qc2757481.KqCustomUtil;
import com.cloudstore.dev.api.util.Util_TableMap;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.biz.KQLeaveRulesComInfo;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.PageIdConst;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.company.SubCompanyComInfo;
import weaver.hrm.moduledetach.ManageDetachComInfo;
import weaver.systeminfo.SystemEnv;
import weaver.systeminfo.systemright.CheckSubCompanyRight;
import weaver.hrm.common.Tools;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期余额列表
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
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        try {
            Boolean isYearMonth = false;
            String rightLevel = HrmUserVarify.getRightLevel("KQLeaveRulesEdit:Edit", user);
            int departmentID = user.getUserDepartment();
            int subcompanyID = user.getUserSubCompany1();
            String monthDaysFirst = "";
            String monthDaysEnd = "";
                /*左侧组织结构树点击的节点类型：0-总部、1-分部、2-部门*/
            int organizationType = Util.getIntValue((String) params.get("organizationType"), 0);
            /*左侧组织结构树点击的节点Id（节点类型为分部时为分部ID）*/
            String organizationId = Util.null2String(params.get("organizationId"));
            /*假期类型的ID(对应的是数据库kq_LeaveRules表中的主键ID)*/
            String ruleId = Util.null2String(params.get("ruleId"));
            /*搜索条件中的年份*/
            String searchYearSelect = Util.null2String(params.get("searchYearSelect"));//日期控件的选择值
            String searchYear = Util.null2String(params.get("searchYear"));//年份
            if (searchYearSelect.equals("5") || searchYearSelect.equals("8")) {
                searchYear = TimeUtil.getDateByOption(searchYearSelect, "0").substring(0, 4);
            }
            new BaseBean().writeLog("==zj==(是否是年月类型)" + JSON.toJSONString(searchYearSelect));
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
            new BaseBean().writeLog("==zj==(是否是年月)" + JSON.toJSONString(isYearMonth));

            /*搜索条件中的人员姓名*/
            String hrmResourceId = Util.null2String(params.get("hrmResourceId"));
            //机构ID以及所有下级机构的ID
            String organizationIds = "";
            if (organizationType == 1) {
                SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
                organizationIds = subCompanyComInfo.getAllChildSubcompanyId(organizationId, organizationIds);
            } else if (organizationType == 2) {
                DepartmentComInfo departmentComInfo = new DepartmentComInfo();
                organizationIds = departmentComInfo.getAllChildDepartId(organizationId, organizationIds);
            }
            if (organizationIds.equals("")) {
                organizationIds = organizationId;
            } else {
                organizationIds = organizationId + organizationIds.trim();
            }
            if (!hrmResourceId.equals("")) {
                organizationType = 3;
                organizationIds = hrmResourceId;
            }

            if (ruleId.equals("") || searchYear.equals("") || (organizationType != 0 && organizationIds.equals(""))) {
                resultMap.put("status", "-1");
                resultMap.put("message", SystemEnv.getHtmlLabelName(388858, user.getLanguage()));//参数有误
                return resultMap;
            }
            //当前日期
            String currentDate = DateUtil.getCurrentDate();

            /**********************************************************************************************************/

            /*获取假期类型的相关设置*/
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();

            /*假期类型名称*/
            String leaveName = Util.formatMultiLang(rulesComInfo.getLeaveName(ruleId), "" + user.getLanguage());

            /*假期类型的最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假*/
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(ruleId), 1);

            /*假期类型的单位是天还是小时*/
            String unitName = rulesComInfo.getUnitName(ruleId, user.getLanguage());//单位名称，天/小时

            /**********************************************************************************************************/

            /*判断该假期类型是否属于 法定年假+福利年假 的类型(暂不支持一个请假类型下既存在"按入职时长+工龄自动发放"的余额发放方式，又存在其他发放方式)*/
            boolean isMixMode = KQLeaveRulesBiz.isMixMode(ruleId);

            /**********************************************************************************************************/

            ManageDetachComInfo manageDetachComInfo = new ManageDetachComInfo();
            //是否开启了人力资源模块的管理分权
            boolean isUseHrmManageDetach = manageDetachComInfo.isUseHrmManageDetach();

            /**********************************************************************************************************/

            /*判断该假期类型是否属于 调休 的类型(暂不支持一个请假类型下既存在"加班时长自动计入余额"的余额发放方式，又存在其他发放方式)*/
            boolean isTiaoXiu = KQLeaveRulesBiz.isTiaoXiu(ruleId);

            RecordSet recordSet = new RecordSet();

            /*如果属于调休，则页面展示与其他假期类型有所不同*/
            if (isTiaoXiu) {
                String sql_b = "";
                String sql_c = "";
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                        || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    if (isYearMonth){
                        sql_b = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountB,sum(extraAmount) allExtraAmountB,sum(baseAmount) allBaseAmountB,sum(usedAmount) allUsedAmountB from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and effectiveDate between '" + monthDaysFirst+"' and '"+monthDaysEnd+ "' and (expirationDate is null or expirationDate='' or expirationDate>='" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                        sql_c = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountC,sum(extraAmount) allExtraAmountC,sum(baseAmount) allBaseAmountC,sum(usedAmount) allUsedAmountC from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and effectiveDate between'" + monthDaysFirst+"' and '"+monthDaysEnd+ "' and (expirationDate is not null and expirationDate<>'' and expirationDate<'" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                    }else {
                        sql_b = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountB,sum(extraAmount) allExtraAmountB,sum(baseAmount) allBaseAmountB,sum(usedAmount) allUsedAmountB from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and belongYear='" + searchYear + "' and (expirationDate is null or expirationDate='' or expirationDate>='" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                        sql_c = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountC,sum(extraAmount) allExtraAmountC,sum(baseAmount) allBaseAmountC,sum(usedAmount) allUsedAmountC from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and belongYear='" + searchYear + "' and (expirationDate is not null and expirationDate<>'' and expirationDate<'" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                    }
                } else {
                    if (isYearMonth){
                        sql_b = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountB,sum(extraAmount) allExtraAmountB,sum(baseAmount) allBaseAmountB,sum(usedAmount) allUsedAmountB from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and effectiveDate between '" + monthDaysFirst+"' and '"+monthDaysEnd+ "' and (expirationDate is null or expirationDate>='" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                        sql_c = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountC,sum(extraAmount) allExtraAmountC,sum(baseAmount) allBaseAmountC,sum(usedAmount) allUsedAmountC from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and effectiveDate between '" + monthDaysFirst+"' and '"+monthDaysEnd+ "' and (expirationDate is not null and expirationDate<'" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                    }else {
                        sql_b = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountB,sum(extraAmount) allExtraAmountB,sum(baseAmount) allBaseAmountB,sum(usedAmount) allUsedAmountB from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and belongYear='" + searchYear + "' and (expirationDate is null or expirationDate>='" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                        sql_c = "select resourceId,sum(tiaoxiuamount) alltiaoxiuamountC,sum(extraAmount) allExtraAmountC,sum(baseAmount) allBaseAmountC,sum(usedAmount) allUsedAmountC from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and belongYear='" + searchYear + "' and (expirationDate is not null and expirationDate<'" + currentDate + "') group by resourceId,leaveRulesId,belongYear ";
                    }
                }
                String backFields = " a.id,a.dspOrder,a.lastName,a.subCompanyId1,a.departmentId,a.companyStartDate,a.workStartDate,a.id resourceId," + ruleId + " as leaveRulesId,0 as allTotalAmount,0 as allUsedAmount,0 as allInvalidAmount,0 as allRestAmount,0 as detailShow," +
                        "b.allBaseAmountB,b.alltiaoxiuamountB,b.allExtraAmountB,b.allUsedAmountB,c.alltiaoxiuamountC,c.allBaseAmountC,c.allExtraAmountC,c.allUsedAmountC ";
                String sqlFrom = " from HrmResource a left join (" + sql_b + ") b on a.id=b.resourceId left join (" + sql_c + ") c on a.id=c.resourceId ";
                String sqlWhere = " where 1=1 and a.status in (0,1,2,3) ";
                String orderBy = " dspOrder,a.id ";
                if (organizationType == 1) {
                    sqlWhere += " and a.subCompanyId1 in (" + organizationIds + ") ";
                } else if (organizationType == 2) {
                    sqlWhere += " and a.departmentId in (" + organizationIds + ") ";
                } else if (organizationType == 3) {
					sqlWhere += " and ("+ Tools.getOracleSQLIn(organizationIds,"a.id")+")";
                }
                new BaseBean().writeLog("==zj==(11调休余额查询sql)" + backFields + sqlFrom + sqlWhere + orderBy);
                //如果开启了分权
                if (isUseHrmManageDetach) {
                    SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
                    String allRightSubComIds = subCompanyComInfo.getRightSubCompany(user.getUID(),"KQLeaveRulesEdit:Edit",0);
                    sqlWhere += " and " + Util.getSubINClause(allRightSubComIds, "a.subCompanyId1", "in");
                }else{
                    if (rightLevel.equals("2")) {
                        // 总部级别的，什么也不返回
                    } else if (rightLevel.equals("1")) { // 分部级别的
                        sqlWhere += " and a.subCompanyId1=" + subcompanyID;
                    } else if (rightLevel.equals("0")) { // 部门级别
                        sqlWhere += " and a.departmentId=" + departmentID;
                    }
                }
                String pageUid = "48d04076-d276-991b-c3b0-860559f71659";
                String tableString = "" +
                        "<table pageId=\"KQ:BalanceOfLeave\" pageUid=\"" + pageUid + "\" tabletype=\"none\" pagesize=\"" + PageIdConst.getPageSize("KQ:BalanceOfLeave", user.getUID(), PageIdConst.HRM) + "\" >" +
                        "   <sql backfields=\"" + backFields + "\" sqlform=\"" + Util.toHtmlForSplitPage(sqlFrom) + "\" sqlwhere=\"" + Util.toHtmlForSplitPage(sqlWhere) + "\"  sqlorderby=\"" + orderBy + "\"  sqlprimarykey=\"id\" sqlsortway=\"asc\" sqlisdistinct=\"true\"/>" +
                        "   <head>" +
                        "       <col width=\"8%\"  text=\"" + SystemEnv.getHtmlLabelName(413, user.getLanguage()) + "\"    column=\"lastName\"/>" +
                        "       <col width=\"12%\" text=\"" + SystemEnv.getHtmlLabelName(141, user.getLanguage()) + "\"    column=\"subCompanyId1\" transmethod=\"weaver.hrm.company.SubCompanyComInfo.getSubCompanyname\" />" +
                        "       <col width=\"12%\" text=\"" + SystemEnv.getHtmlLabelName(124, user.getLanguage()) + "\"    column=\"departmentId\" transmethod=\"weaver.hrm.company.DepartmentComInfo.getDepartmentname\" />" +
                        "       <col width=\"8%\"  text=\"" + SystemEnv.getHtmlLabelName(23519, user.getLanguage()) + "\"  column=\"workStartDate\" />" +
                        "       <col width=\"8%\"  text=\"" + SystemEnv.getHtmlLabelName(1516, user.getLanguage()) + "\"   column=\"companyStartDate\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(388246, user.getLanguage()) + unitName + "\" column=\"allTotalAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getAllTotalAmount\" otherpara=\"column:allBaseAmountB+column:allExtraAmountB+column:allBaseAmountC+column:allExtraAmountC+column:alltiaoxiuamountB+column:alltiaoxiuamountC\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(26642, user.getLanguage()) + unitName + "\"  column=\"allUsedAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getAllUsedAmount\" otherpara=\"column:allUsedAmountB+column:allUsedAmountC\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389611, user.getLanguage()) + unitName + "\"  column=\"allInvalidAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getAllInvalidAmount\" otherpara=\"column:allBaseAmountC+column:allExtraAmountC+column:allUsedAmountC+column:alltiaoxiuamountC\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(25723, user.getLanguage()) + unitName + "\"  column=\"allRestAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getAllRestAmount\" otherpara=\"column:allBaseAmountB+column:allExtraAmountB+column:allUsedAmountB+column:alltiaoxiuamountB\" />" +
                        "       <col width=\"8%\"  text=\"" + SystemEnv.getHtmlLabelName(17463, user.getLanguage()) + "\"  column=\"detailShow\" transmethod=\"com.engine.kq.util.KQTransMethod.getDetailShow\" otherpara=\"" + user.getLanguage() + "\" />" +
                        "   </head>" +
                        "</table>";
                String sessionKey = pageUid + "_" + Util.getEncrypt(Util.getRandom());
                Util_TableMap.setVal(sessionKey, tableString);
                resultMap.put("sessionkey", sessionKey);
                return resultMap;
            }

            /**********************************************************************************************************/

            String backFields = " a.id,a.dspOrder,a.lastName,a.subcompanyId1,a.departmentId,a.companyStartDate,a.workStartDate,a.id resourceId," + ruleId + " as leaveRulesId,b.baseAmount,b.usedAmount,b.extraAmount,b.status," +
                    "b.baseAmount2,b.usedAmount2,b.extraAmount2,b.belongYear,b.id canUseAmount2,b.id canUseAmount,b.id restAmount,b.id restAmount2  ";
            String sqlFrom = " from HrmResource a left join kq_balanceOfLeave b on a.id=b.resourceId and leaveRulesId=" + ruleId + " and belongYear='" + searchYear + "' ";
            String sqlWhere = " where 1=1 and a.status in (0,1,2,3) and (isDelete is null or isDelete<>1) ";
            String orderBy = " dspOrder,a.id ";
            if (organizationType == 1) {
                sqlWhere += " and a.subcompanyId1 in (" + organizationIds + ")";
            } else if (organizationType == 2) {
                sqlWhere += " and a.departmentId in (" + organizationIds + ")";
            } else if (organizationType == 3) {
               sqlWhere += " and ("+ Tools.getOracleSQLIn(organizationIds,"a.id")+")";
            }
            //如果开启了分权
            if (isUseHrmManageDetach) {
                SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
                String allRightSubComIds = subCompanyComInfo.getRightSubCompany(user.getUID(),"KQLeaveRulesEdit:Edit",0);
                sqlWhere += " and " + Util.getSubINClause(allRightSubComIds, "a.subCompanyId1", "in");
            }else{
                if (rightLevel.equals("2")) {
                    // 总部级别的，什么也不返回
                } else if (rightLevel.equals("1")) { // 分部级别的
                    sqlWhere += " and a.subCompanyId1=" + subcompanyID;
                } else if (rightLevel.equals("0")) { // 部门级别
                    sqlWhere += " and a.departmentId=" + departmentID;
                }
            }
            String pageUid = "7907a963-04f3-62fc-7263-9486830dc6ad";
            String tableString = "" +
                    "<table pageId=\"KQ:BalanceOfLeave\" pageUid=\"" + pageUid + "\" tabletype=\"none\" pagesize=\"" + PageIdConst.getPageSize("KQ:BalanceOfLeave", user.getUID(), PageIdConst.HRM) + "\" >" +
                    "   <sql backfields=\"" + backFields + "\" sqlform=\"" + sqlFrom + "\" sqlwhere=\"" + Util.toHtmlForSplitPage(sqlWhere) + "\"  sqlorderby=\"" + orderBy + "\"  sqlprimarykey=\"id\" sqlsortway=\"asc\" sqlisdistinct=\"true\"/>" +
                    "   <head>" +
                    "       <col width=\"8%\" text=\"" + SystemEnv.getHtmlLabelName(413, user.getLanguage()) + "\" column=\"lastname\"/>" +
                    "       <col width=\"12%\" text=\"" + SystemEnv.getHtmlLabelName(141, user.getLanguage()) + "\" column=\"subcompanyId1\" transmethod=\"weaver.hrm.company.SubCompanyComInfo.getSubCompanyname\" />" +
                    "       <col width=\"12%\" text=\"" + SystemEnv.getHtmlLabelName(124, user.getLanguage()) + "\" column=\"departmentId\" transmethod=\"weaver.hrm.company.DepartmentComInfo.getDepartmentname\" />" +
                    "       <col width=\"8%\" text=\"" + SystemEnv.getHtmlLabelName(23519, user.getLanguage()) + "\" column=\"workStartDate\" />" +
                    "       <col width=\"8%\" text=\"" + SystemEnv.getHtmlLabelName(1516, user.getLanguage()) + "\" column=\"companyStartDate\" transmethod=\"com.engine.kq.util.KQTransMethod.getCompanyStartDate\" otherpara=\"column:id\" />";
            if (!isMixMode) {
                tableString += "" +
                        "       <col width=\"10%\" text=\"" + leaveName + SystemEnv.getHtmlLabelName(17638, user.getLanguage()) + unitName + "\" column=\"baseAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(509827, user.getLanguage()) + leaveName + unitName + "\" column=\"canUseAmount\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount+neither\" transmethod=\"com.engine.kq.util.KQTransMethod.getCanUseAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(130286, user.getLanguage()) + leaveName + unitName + "\" column=\"extraAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(26642, user.getLanguage()) + leaveName + unitName + "\" column=\"usedAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(25723, user.getLanguage()) + leaveName + unitName + "\" column=\"restAmount\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount+column:extraAmount+column:usedAmount+neither\" transmethod=\"com.engine.kq.util.KQTransMethod.getRestAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(15591, user.getLanguage()) + "\" column=\"leaveRulesId\" otherpara=\"column:resourceId+" + searchYear + "+" + user.getLanguage() + "+column:effectiveDate+column:expirationDate\" transmethod=\"com.engine.kq.util.KQTransMethod.getBalanceStatusShow\" />";
            } else {
                tableString += "" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(500228, user.getLanguage()) + unitName + "\" column=\"baseAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(10000810, Util.getIntValue(user.getLanguage())) + unitName + "\" column=\"canUseAmount\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount+legal\" transmethod=\"com.engine.kq.util.KQTransMethod.getCanUseAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389745, user.getLanguage()) + unitName + "\" column=\"extraAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389743, user.getLanguage()) + unitName + "\" column=\"usedAmount\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389747, user.getLanguage()) + unitName + "\" column=\"restAmount\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount+column:extraAmount+column:usedAmount+legal\" transmethod=\"com.engine.kq.util.KQTransMethod.getRestAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(500229, user.getLanguage()) + unitName + "\" column=\"baseAmount2\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(10000811, Util.getIntValue(user.getLanguage())) + unitName + "\" column=\"canUseAmount2\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount2+welfare\" transmethod=\"com.engine.kq.util.KQTransMethod.getCanUseAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389746, user.getLanguage()) + unitName + "\" column=\"extraAmount2\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389744, user.getLanguage()) + unitName + "\" column=\"usedAmount2\" transmethod=\"com.engine.kq.util.KQTransMethod.getOriginalShow\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(389748, user.getLanguage()) + unitName + "\" column=\"restAmount2\" otherpara=\"column:id+column:leaveRulesId+column:belongYear+column:baseAmount2+column:extraAmount2+column:usedAmount2+welfare\" transmethod=\"com.engine.kq.util.KQTransMethod.getRestAmount\" />" +
                        "       <col width=\"10%\" text=\"" + SystemEnv.getHtmlLabelName(15591, user.getLanguage()) + "\" column=\"leaveRulesId\" otherpara=\"column:resourceId+" + searchYear + "+" + user.getLanguage() + "+column:effectiveDate+column:expirationDate\" transmethod=\"com.engine.kq.util.KQTransMethod.getBalanceStatusShow\" />";
            }
            tableString += "" +
                    "   </head>" +
                    "</table>";
            String sessionKey = pageUid + "_" + Util.getEncrypt(Util.getRandom());
            Util_TableMap.setVal(sessionKey, tableString);
            resultMap.put("sessionkey", sessionKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }
}

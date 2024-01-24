package com.api.customization.qc2758032.util;

import com.engine.kq.biz.*;
import weaver.general.BaseBean;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.engine.kq.cmd.attendanceButton.ButtonStatusEnum;
import com.engine.kq.enums.KqSplitFlowTypeEnum;
import com.engine.kq.log.KQLog;
import com.engine.kq.util.KQDurationCalculatorUtil;

import java.text.DecimalFormatSymbols;
import java.util.Map.Entry;

import com.engine.kq.util.KQTransMethod;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.file.Prop;
import weaver.general.BaseBean;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

import java.text.DecimalFormat;
import java.util.*;

public class KqReportBizCustom extends BaseBean {

    private static DecimalFormat df = new DecimalFormat("0.00");

    private DecimalFormatSymbols symbols = new DecimalFormatSymbols();


    public String format(double value) {
//    DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        df.setMaximumFractionDigits(5);
        df.setDecimalFormatSymbols(symbols);
        return df.format(value);
    }


    private KQLog kqLog = new KQLog();
    /**
     * 初始化当天考勤数据
     */
    public void initKQReportData(){
        String date = DateUtil.getCurrentDate();
        RecordSet rs = new RecordSet();
        String sql = "";
        try{
            boolean hasInit = false;
            sql = "select 1 from kq_report_check where check_date = ? ";
            rs.executeQuery(sql,date);
            if(rs.next()) {
                hasInit = true;
            }

            String beforeyesterday = DateUtil.addDate(date,-2);
            String yesterday = DateUtil.addDate(date,-1);
            if(!hasInit){
                //这里做过功能是每天都刷下考勤缓存 因为这个缓存也是异步的，一点都不稳定
                kqLog.info("begin do KQOneStopUp refreshCominfo invoke ...");
                new KQOneStopUp().refreshCominfo();
                kqLog.info("end do KQOneStopUp refreshCominfo invoke ...");

                KQFormatBiz kqFormatBiz = new KQFormatBiz();
                kqFormatBiz.clearFormatPool();
                //格式化上上天数据
                kqFormatBiz.formatDateByKQDate(beforeyesterday);
                //格式化上一天数据
                kqFormatBiz.formatDateByKQDate(yesterday);
                //初始化今天数据
                kqFormatBiz.formatDateByKQDate(date);

                sql = " insert into kq_report_check(check_date) values (?)";
                rs.executeUpdate(sql,date);

                writeLog(date+"执行考勤报表数据格式化成功！");
            }
        }catch (Exception e){
            writeLog(e);
        }
    }

    public Map<String, String> getModel_ShiftRule(String i,String serialId) {
        RecordSet rs = new RecordSet();
        HashMap<String,String> sectionMap = new HashMap<>();
        String getStartWorkSections = "select * from kq_ShiftOnOffWorkSections where  SERIALID=? and record=? ";
        rs.executeQuery(getStartWorkSections,serialId,i);
        while (rs.next()) {
            String onoffworktype = Util.null2String(rs.getString("onoffworktype"));
            String clockinnot = Util.null2s(Util.null2String(rs.getString("clockinnot")), "0");
            sectionMap.put(onoffworktype, clockinnot);
        }
        return sectionMap;
    }

    /***
     * 获取考勤状态
     * @param resourceId
     * @param fromdate
     * @param todate
     * @param isAll
     * @return
     */
    public List<Object> getKqDateInfo(String resourceId, String fromdate, String todate, boolean isAll) {
        List<Object> kqdates = new ArrayList<>();
        RecordSet rs = new RecordSet();
        String sql = "";
        try {
            Map<String, Object> kqdate = null;
            List<Object> datas = null;
            Map<String,Object> data = null;
            sql = " select kqdate,serialid, workbegindate, workbegintime,workenddate, workendtime," +
                    " signintime,signouttime,beLateMins,graveBeLateMins,leaveEarlyMins,graveLeaveEarlyMins," +
                    " absenteeismMins, forgotCheckMins,forgotBeginWorkCheckMins,otherinfo,serialnumber "+
                    " from kq_format_detail " +
                    " where resourceid=? and kqdate>=? and kqdate<=?  ";
            if(!isAll){
                if(rs.getDBType().equalsIgnoreCase("oracle") || Util.null2String(rs.getOrgindbtype()).equals("dm") || Util.null2String(rs.getOrgindbtype()).equals("st")) {
                    sql += " and nvl(beLateMins,0)+nvl(graveBeLateMins,0)+nvl(leaveEarlyMins,0)+nvl(graveLeaveEarlyMins,0)+nvl(absenteeismMins,0)+nvl(forgotCheckMins,0)+nvl(forgotBeginWorkCheckMins,0)>0 ";
                }else if((rs.getDBType()).equalsIgnoreCase("mysql")){
                    sql += " and ifnull(beLateMins,0)+ifnull(graveBeLateMins,0)+ifnull(leaveEarlyMins,0)+ifnull(graveLeaveEarlyMins,0)+ifnull(absenteeismMins,0)+ifnull(forgotCheckMins,0)+ifnull(forgotBeginWorkCheckMins,0)>0 ";
                }else {
                    sql += " and isnull(beLateMins,0)+isnull(graveBeLateMins,0)+isnull(leaveEarlyMins,0)+isnull(graveLeaveEarlyMins,0)+isnull(absenteeismMins,0)+isnull(forgotCheckMins,0)+isnull(forgotBeginWorkCheckMins,0)>0 ";
                }
            }
            sql += " order by kqdate ";

            rs.executeQuery(sql,resourceId,fromdate, todate);
            while(rs.next()){
                String serialnumber = Util.null2String(rs.getString("serialnumber"));
                String serialid = Util.null2String(rs.getString("serialid"));
                String workbegindate = Util.null2String(rs.getString("workbegindate"));
                String workbegintime = Util.null2String(rs.getString("workbegintime"));
                String workenddate = Util.null2String(rs.getString("workenddate"));
                String workendtime = Util.null2String(rs.getString("workendtime"));
                String signintime = Util.null2String(rs.getString("signintime"));
                String signouttime = Util.null2String(rs.getString("signouttime"));
                int beLateMins = rs.getInt("beLateMins");
                int graveBeLateMins = rs.getInt("graveBeLateMins");
                int leaveEarlyMins = rs.getInt("leaveEarlyMins");
                int graveLeaveEarlyMins = rs.getInt("graveLeaveEarlyMins");
                int absenteeismMins = rs.getInt("absenteeismMins");
                int forgotCheckMins = rs.getInt("forgotCheckMins");
                int forgotBeginWorkCheckMins = rs.getInt("forgotBeginWorkCheckMins");
                String otherinfo = Util.null2String(rs.getString("otherinfo"));

                String shift_begindate = "";
                String shift_beginworktime = "";
                String shift_enddate = "";
                String shift_endworktime = "";
                if(otherinfo.length() > 0){
                    JSONObject otherinfo_object = JSONObject.parseObject(otherinfo);
                    if(otherinfo_object != null && !otherinfo_object.isEmpty()){
                        JSONObject shiftRule = (JSONObject) otherinfo_object.get("shiftRule");
                        if(shiftRule != null && !shiftRule.isEmpty()){
                            shift_begindate = Util.null2String(shiftRule.get("shift_begindate"));
                            shift_beginworktime = Util.null2String(shiftRule.get("shift_beginworktime"));
                            shift_enddate = Util.null2String(shiftRule.get("shift_enddate"));
                            shift_endworktime = Util.null2String(shiftRule.get("shift_endworktime"));
                            if(shift_begindate.length() > 0){
                                workbegindate = shift_begindate;
                            }
                            if(shift_beginworktime.length() > 0){
                                workbegintime = shift_beginworktime;
                            }
                            if(shift_enddate.length() > 0){
                                workenddate = shift_enddate;
                            }
                            if(shift_endworktime.length() > 0){
                                workendtime = shift_endworktime;
                            }
                        }
                    }
                }

                kqdate = new HashMap<>();
                datas = new ArrayList<>();
                kqdate.put("kqdate",Util.null2String(rs.getString("kqdate")));
                if(workbegindate.length()>0){
                    data = new HashMap<>();
                    data.put("workbegindate",workbegindate);
                    data.put("workbegintime",workbegintime);
                    data.put("signintime",signintime);

                    data.put("workenddate",workenddate);
                    data.put("workendtime",workendtime);
                    data.put("signouttime",signouttime);
                    String status = "";
                    if(beLateMins>0||graveBeLateMins>0){
                        status = ButtonStatusEnum.BELATE.getStatusCode();
                    }
                    if(forgotBeginWorkCheckMins>0){
                        if(status.length()>0)status+=",";
                        status += ButtonStatusEnum.NOSIGN_ON.getStatusCode();
                    }
                    if(leaveEarlyMins>0||graveLeaveEarlyMins>0){
                        if(status.length()>0)status+=",";
                        status += ButtonStatusEnum.LEAVEERALY.getStatusCode();
                    }
                    if(absenteeismMins>0){
                        int isondutyfreecheck =0;
                        int isoffdutyfreecheck =0;
                        Map<String, String> model_ShiftRule = getModel_ShiftRule(serialnumber, serialid);
                        Iterator iter = model_ShiftRule.entrySet().iterator();
                        while (iter.hasNext()) {
                            Entry entry = (Entry) iter.next();
                            String key = Util.null2String(entry.getKey());
                            String value = Util.null2String(entry.getValue());
                            if(key.equals("start")&&value.equals("1")){
                                isondutyfreecheck = 1;
                            }
                            if(key.equals("end")&&value.equals("1")){
                                isoffdutyfreecheck = 1;
                            }
                        }
                        data.put("start",isondutyfreecheck);
                        data.put("end",isoffdutyfreecheck);
                        if(status.length()>0)status+=",";
                        status += ButtonStatusEnum.ABSENT.getStatusCode();
                    }
                    if(forgotCheckMins>0){
                        if(status.length()>0)status+=",";
                        status += ButtonStatusEnum.NOSIGN.getStatusCode();
                    }
                    data.put("status",status);
                    datas.add(data);
                }

                kqdate.put("checkInfo",datas);
                kqdates.add(kqdate);
            }
        } catch (Exception e) {
            writeLog(e);
        }
        return kqdates;
    }

    /**
     * 获取真实的开始日期和结束日期，没有权限的人最大只能查询本季
     * @param fromDate
     * @param toDate
     * @param user
     * @return
     */
    public Map<String, String> realDate(String fromDate, String toDate, User user, String reportType) {
        Map<String, String> dateMap = new HashMap<>();
        dateMap.put("fromDate", fromDate);
        dateMap.put("toDate", toDate);
        boolean hasRight = new com.engine.kq.biz.KQReportBiz().hasReportRight(reportType,""+user.getUID());
        boolean kq_personal_reportsearch = KQSettingsBiz.showLeaveTypeSet("kq_personal_reportsearch");
        if(!hasRight && kq_personal_reportsearch) {  // 开启开关，且没有权限的账号，最大查询只支持到本季
            String seasonFromDate = TimeUtil.getDateByOption("4","0");
            String seasonToDate = TimeUtil.getDateByOption("4","1");
            if(toDate.compareTo(seasonFromDate) < 0 || fromDate.compareTo(seasonToDate) > 0) {
                dateMap.put("fromDate", "-1");
                dateMap.put("toDate", "-1");
                return dateMap;
            } else if(fromDate.compareTo(seasonFromDate) < 0) {
                fromDate = seasonFromDate;
                if(toDate.compareTo(seasonToDate) > 0) {
                    toDate = seasonToDate;
                }
            } else if(fromDate.compareTo(seasonFromDate) >= 0) {
                if(toDate.compareTo(seasonToDate) > 0) {
                    toDate = seasonToDate;
                }
            }
        }
        dateMap.put("fromDate", fromDate);
        dateMap.put("toDate", toDate);
        return dateMap;
    }

    /**
     * 是否有考勤报表相关权限
     * @param reportType 1:考勤汇总报表; 2:每日统计报表; 3:原始打卡记录; 4:员工假期余额
     * @param userId
     * @return
     */
    public boolean hasReportRight(String reportType, String userId){
        String sql = "";
        RecordSet rs = new RecordSet();
        sql = " select sharelevel,subcomid, deptid,userid,jobtitleid,foralluser from kq_reportshare where resourceid=? ";
        if(reportType.length()>0 ){
            sql += " and (reportname =0 or reportname =" + reportType+")";
        }
        rs.executeQuery(sql,userId);
        if(rs.next()) {
            return true;
        }
        return false;
    }

    /**
     * 将操作导出四大考勤报表的日志记录到数据库中，谁操作的导出，什么时间操作的导出，导出的条件都存储下来
     * @param params
     * @param user
     */
    public void insertKqReportExportLog(Map<String, Object> params, User user) {
        RecordSet rs = new RecordSet();
        String exportParams = JSON.toJSONString(params);
        String sql = "insert into kq_exportreport_log(operatorid,exportparams) values(?,?)";
        rs.executeUpdate(sql, user.getUID(), exportParams);
    }

    /**
     * 获取报表权限
     * @param reportType 报表类型
     * @param userId 用户id
     * @param tableExt 别名
     * @return
     */
    public String getReportRight(String reportType, String userId, String tableExt){
        String rightSql = "";
        String sql = "";
        RecordSet rs = new RecordSet();
        try{
            List<String> userAllUserIds = new ArrayList<>();
            List<String> userAllDeptIds = new ArrayList<>();
            List<String> userAllSubCompanyIds = new ArrayList<>();
            List<String> userAllJobtitleIds = new ArrayList<>();
            boolean forAllUser = false;
            sql = " select sharelevel,subcomid, deptid,userid,jobtitleid,foralluser from kq_reportshare where resourceid=? ";
            if(reportType.length()>0 ){
                sql += " and (reportname =0 or reportname =" + reportType+")";
            }
            rs.executeQuery(sql,userId);
            while(rs.next()){
                int sharelevel = rs.getInt("sharelevel");
                if(sharelevel==0){//分部
                    if(Util.null2String(rs.getString("subcomid")).length()>0){
                        userAllSubCompanyIds.add(rs.getString("subcomid"));
                    }
                }else if(sharelevel==1){//部门
                    if(Util.null2String(rs.getString("deptid")).length()>0) {
                        userAllDeptIds.add(rs.getString("deptid"));
                    }
                }else if(sharelevel==2){//人员
                    if(Util.null2String(rs.getString("userid")).length()>0) {
                        userAllUserIds.add(rs.getString("userid"));
                    }
                }else if(sharelevel==3){//岗位
                    if(Util.null2String(rs.getString("jobtitleid")).length()>0) {
                        userAllJobtitleIds.add(rs.getString("jobtitleid"));
                    }
                }else if(sharelevel==4){//所有人
                    forAllUser = true;
                    userAllUserIds.clear();
                    userAllDeptIds.clear();
                    userAllSubCompanyIds.clear();
                    userAllJobtitleIds.clear();
                    break;
                }
            }

            if(!forAllUser) {
                for (int sharelevel = 0; sharelevel < 4; sharelevel++) {
                    if (sharelevel == 0) {//分部
                        if (userAllSubCompanyIds.size() > 0) {
                            if(rightSql.length()>0)rightSql+= " or ";
                            rightSql += tableExt+".subcompanyid1 in(" + String.join(",", userAllSubCompanyIds) + ")";
                        }
                    } else if (sharelevel == 1) {//部门
                        if (userAllDeptIds.size() > 0) {
                            if(rightSql.length()>0)rightSql+= " or ";
                            rightSql += tableExt+".departmentid in(" + String.join(",", userAllDeptIds) + ")";
                        }
                    } else if (sharelevel == 2) {//人员
                        if (userAllUserIds.size() > 0) {
                            if(rightSql.length()>0)rightSql+= " or ";
                            rightSql += tableExt+".id in(" + String.join(",", userAllUserIds) + ")";
                        }
                    } else if (sharelevel == 3) {//岗位
                        if (userAllJobtitleIds.size() > 0) {
                            if(rightSql.length()>0)rightSql+= " or ";
                            rightSql += tableExt+".jobtitle in(" + String.join(",", userAllJobtitleIds) + ")";
                        }
                    }
                }

                //可以看自己和下属的考勤
                String selfSql = " ("+tableExt+".id = "+userId+" or "+tableExt+".managerstr like '%,"+userId+",%')";
                if(rightSql.length()>0){
                    rightSql = " and ((" +rightSql+") or "+selfSql+" ) ";
                }else{
                    rightSql = " and "+selfSql;
                }

            }
        }catch (Exception e){
            writeLog(e);
        }
        return rightSql;
    }

    public static String getUnitType(KQReportFieldComInfo kqReportFieldComInfo, User user){
        String unitTypeName = "";
        String unitType = Util.null2String(kqReportFieldComInfo.getUnittype());
        String parentid = Util.null2String( kqReportFieldComInfo.getParentid());
        String fieldName = Util.null2String(kqReportFieldComInfo.getFieldname());
        if(kqReportFieldComInfo.getIsLeaveType().equals("1")){
            if(fieldName.equals("businessLeave")){
                unitType = KQTravelRulesBiz.getMinimumUnit().equals("3")?"2":"1";//单位类型
            }else if(fieldName.equals("officialBusiness")){
                unitType = KQExitRulesBiz.getMinimumUnit().equals("3")?"2":"1";//单位类型
            }else if(parentid.equals("overtime") || parentid.equals("overtime_nonleave") || parentid.equals("overtime_4leave")){
                unitType = (KQOvertimeRulesBiz.getMinimumUnit()==3 || KQOvertimeRulesBiz.getMinimumUnit()==5 ||KQOvertimeRulesBiz.getMinimumUnit()==6)?"2":"1";//单位类型
            }
        }
        if(Util.null2String(unitType).length()>0){
            if(unitType.equals("1")){
                unitTypeName=SystemEnv.getHtmlLabelName(1925, user.getLanguage());
            }else if(unitType.equals("2")){
                unitTypeName=SystemEnv.getHtmlLabelName(391, user.getLanguage());
            }else if(unitType.equals("3")){
                unitTypeName=SystemEnv.getHtmlLabelName(18083, user.getLanguage());
            }
        }
        return unitTypeName;
    }

    /**
     * 为每日统计报表做流程数据查询
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getDailyFlowData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        try{
            datas.putAll(getDailyFlowLeaveData(params,user));
            datas.putAll(getDailyFlowEvectionOutData(params,user));
            datas.putAll(getDailyFlowOverTimeData(params,user));
            datas.putAll(getDailyFlowLeaveBackData(params,user));

            datas.putAll(getSignDetailInfoData(params,user));
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    public Map<String,Object> getFlowData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        try{
            datas.putAll(getFlowLeaveData(params,user));
            datas.putAll(getFlowEvectionOutData(params,user));
            datas.putAll(getFlowOverTimeDataNew(params,user));
            datas.putAll(getFlowOtherData(params,user));
            datas.putAll(getFlowLeaveBackData(params,user));
            datas.putAll(getFlowProcessChangeData(params,user));

            datas.putAll(getCardMap(params,user));
            datas.putAll(getOverTime(params,user));
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取出差和公出数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getFlowEvectionOutData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            String[] tables = new String[]{KqSplitFlowTypeEnum.EVECTION.getTablename(),//出差
                    KqSplitFlowTypeEnum.OUT.getTablename()};//公出

            String minimumUnit = "";//单位类型
            double proportion = 0.00;//换算关系

            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            for(String table : tables){
                sql = " select resourceid, durationrule, sum(duration) as val from hrmresource a, "+table+" b "+
                        " where a.id = b.resourceid and (b.status is null or b.status<>1) and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' "+sqlWhere+
                        " group by resourceid, durationrule ";
                rs.execute(sql);
                while (rs.next()) {
                    String resourceid = rs.getString("resourceid");
                    double value = rs.getDouble("val");
                    String durationrule = rs.getString("durationrule");

                    String flowType = "";
                    if(KqSplitFlowTypeEnum.EVECTION.getTablename().equals(table)){
                        flowType = "businessLeave";
                        minimumUnit = KQTravelRulesBiz.getMinimumUnit();//单位类型
                        proportion = Util.getDoubleValue(KQTravelRulesBiz.getHoursToDay());//换算关系

                    }else if(KqSplitFlowTypeEnum.OUT.getTablename().equals(table)){
                        flowType = "officialBusiness";
                        minimumUnit = KQExitRulesBiz.getMinimumUnit();//单位类型
                        proportion = Util.getDoubleValue(KQExitRulesBiz.getHoursToDay());//换算关系
                    }

                    if(KQUnitBiz.isLeaveHour(minimumUnit)){//按小时
                        if(!KQUnitBiz.isLeaveHour(durationrule)){
                            if(proportion>0) value = value*proportion;
                        }
                    }else{//按天
                        if(KQUnitBiz.isLeaveHour(durationrule)){
                            if(proportion>0) value = value/proportion;
                        }
                    }

                    String key = resourceid+"|"+flowType;
                    if(datas.containsKey(key)){
                        value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                    }
                    //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                    df.setMaximumFractionDigits(5);
                    datas.put(key, format(value));
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取每日的出差和公出数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getDailyFlowEvectionOutData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            String[] tables = new String[]{KqSplitFlowTypeEnum.EVECTION.getTablename(),//出差
                    KqSplitFlowTypeEnum.OUT.getTablename()};//公出

            String minimumUnit = "";//单位类型
            double proportion = 0.00;//换算关系

            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            for(String table : tables){
                sql = " select resourceid, durationrule, sum(duration) as val,belongdate from hrmresource a, "+table+" b "+
                        " where a.id = b.resourceid and (b.status is null or b.status<>1) and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' "+sqlWhere+
                        " group by resourceid, durationrule,belongdate ";
                rs.execute(sql);
                while (rs.next()) {
                    String resourceid = rs.getString("resourceid");
                    String belongdate = rs.getString("belongdate");
                    double value = rs.getDouble("val");
                    String durationrule = rs.getString("durationrule");

                    String flowType = "";
                    if(KqSplitFlowTypeEnum.EVECTION.getTablename().equals(table)){
                        flowType = "businessLeave";
                        minimumUnit = KQTravelRulesBiz.getMinimumUnit();//单位类型
                        proportion = Util.getDoubleValue(KQTravelRulesBiz.getHoursToDay());//换算关系
                    }else if(KqSplitFlowTypeEnum.OUT.getTablename().equals(table)){
                        flowType = "officialBusiness";
                        minimumUnit = KQExitRulesBiz.getMinimumUnit();//单位类型
                        proportion = Util.getDoubleValue(KQExitRulesBiz.getHoursToDay());//换算关系
                    }

                    if(KQUnitBiz.isLeaveHour(minimumUnit)){//按小时
                        if(!KQUnitBiz.isLeaveHour(durationrule)){
                            if(proportion>0) value = value*proportion;
                        }
                    }else{//按天
                        if(KQUnitBiz.isLeaveHour(durationrule)){
                            if(proportion>0) value = value/proportion;
                        }
                    }

                    String key = resourceid+"|"+belongdate+"|"+flowType;
                    if(datas.containsKey(key)){
                        value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                    }
                    //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                    df.setMaximumFractionDigits(5);
                    datas.put(key, format(value));
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取请假数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getFlowLeaveData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, newleavetype, durationrule, sum(duration) as val from hrmresource a, "+KqSplitFlowTypeEnum.LEAVE.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere	+
                    " group by resourceid, newleavetype, durationrule ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String newleavetype = rs.getString("newleavetype");
                String durationrule = rs.getString("durationrule");
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");

                double proportion = Util.getDoubleValue(kqLeaveRulesComInfo.getProportion(newleavetype));
                if(KQUnitBiz.isLeaveHour(newleavetype, kqLeaveRulesComInfo)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|leaveType_"+newleavetype;
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key,format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }


    //获取打卡数据用于判断外勤，补卡等信息===============================
    public Map<String,Object> getCardMap(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        //rs.writeLog("getCardMap="+ JSONObject.toJSONString(params));
        try{
            String show_card_source = Util.null2String(params.get("show_card_source"));
            if(!"1".equals(show_card_source)){//下面的日历都是实时拼接，这里还是控制下数据库的交互次数，虽然用起来没啥用-.-
                return datas;
            }

            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            KQTransMethod kqTransMethod = new KQTransMethod();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            rs.writeLog("jsonObj="+ jsonObj.toJSONString());
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and a.id in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }
            //em7外勤、云桥外勤数据同步、钉钉外勤打卡、微信外勤转考勤（补卡先留一个口子，以防客户要）
//      sqlWhere += " and (signfrom like 'card%' or signfrom='e9_mobile_out' or signfrom='EMSyn_out' or signfrom='DingTalk_out' or signfrom='Wechat_out') ";

            sql = " select a.id,b.signdate,b.id as signid,b.signfrom from hrmresource a, hrmschedulesign b "+
                    " where a.id = b.userid and b.signdate >='"+fromDate+"' and b.signdate <='"+toDate+"' " +sqlWhere	+
                    " order by a.id,b.signdate ";
            rs.execute(sql);
//      kqLog.info("card.sql="+sql);
            while (rs.next()) {
                String resourceid = Util.null2String(rs.getString("id"));
                String signdate = Util.null2String(rs.getString("signdate"));
                String signid = Util.null2String(rs.getString("signid"));
                String signfrom = Util.null2String(rs.getString("signfrom"));
                String signFromShow = kqTransMethod.getSignFromShow(signfrom, ""+user.getLanguage());
                String key = resourceid+"|"+signdate;

                datas.put(signid,signFromShow);
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

//获取打卡数据用于判断外勤，补卡等信息===============================

    /**
     * 获取销假数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getFlowLeaveBackData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, newleavetype, durationrule, sum(duration) as val from hrmresource a, "+KqSplitFlowTypeEnum.LEAVEBACK.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere	+
                    " group by resourceid, newleavetype, durationrule ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String newleavetype = rs.getString("newleavetype");
                String durationrule = rs.getString("durationrule");
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");

                double proportion = Util.getDoubleValue(kqLeaveRulesComInfo.getProportion(newleavetype));
                if(KQUnitBiz.isLeaveHour(newleavetype,kqLeaveRulesComInfo)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|leavebackType_"+newleavetype;
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key,format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取考勤变更流程数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getFlowProcessChangeData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            String minimumUnit = "";//单位类型
            double proportion = 0.00;//换算关系

            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, durationrule, changetype,sum(duration) as val from hrmresource a, "+KqSplitFlowTypeEnum.PROCESSCHANGE.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' "+sqlWhere+
                    " group by resourceid, durationrule,changetype ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                int changetype = Util.getIntValue(rs.getString("changetype"));

                String flowType = "";
                if(KqSplitFlowTypeEnum.EVECTION.getFlowtype() == changetype){
                    flowType = "businessLeave_back";
                    proportion = Util.getDoubleValue(KQTravelRulesBiz.getHoursToDay());//换算关系
                    minimumUnit = KQTravelRulesBiz.getMinimumUnit();
                }else if(KqSplitFlowTypeEnum.OUT.getFlowtype() == changetype){
                    flowType = "officialBusiness_back";
                    minimumUnit = KQExitRulesBiz.getMinimumUnit();
                    proportion = Util.getDoubleValue(KQExitRulesBiz.getHoursToDay());//换算关系
                }
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");
                String durationrule = rs.getString("durationrule");

                if(KQUnitBiz.isLeaveHour(minimumUnit)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|"+flowType;
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key, format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取每日请假数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getDailyFlowLeaveData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, newleavetype, durationrule, sum(duration) as val,belongdate from hrmresource a, "+KqSplitFlowTypeEnum.LEAVE.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere	+
                    " group by resourceid, newleavetype, durationrule,belongdate ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String belongdate = rs.getString("belongdate");
                String newleavetype = rs.getString("newleavetype");
                String durationrule = rs.getString("durationrule");
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");

                double proportion = Util.getDoubleValue(kqLeaveRulesComInfo.getProportion(newleavetype));
                if(KQUnitBiz.isLeaveHour(newleavetype,kqLeaveRulesComInfo)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|"+belongdate+"|leaveType_"+newleavetype;
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key,format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }


    public Map<String,Object> getSignDetailInfoData(Map<String,Object> params, User user){
        Map<String, Object> data = new HashMap<>();
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String isneedcal = Util.null2String(params.get("isneedcal"));
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            //=================================
//      Map<String, Object> data = new HashMap<>();
            Map<String,Object> signStatusInfo = null;
            KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
            sql = " select kqdate,resourceid,serialid,serialnumber,workbegindate,workbegintime, " +
                    " workenddate,workendtime,workmins,signindate,signintime,signoutdate,signouttime, \n" +
                    " attendanceMins,belatemins,graveBeLateMins,leaveearlymins,graveLeaveEarlyMins,absenteeismmins,forgotcheckMins,forgotBeginWorkCheckMins," +
                    " leaveMins,leaveInfo,evectionMins,outMins,signinid,signoutid \n" +
                    " from hrmresource a,kq_format_detail b \n" +
                    " where a.id = b.resourceid " +sqlWhere+
                    " and b.kqdate >='" + fromDate + "' and b.kqdate<='"+toDate+"' \n" +
                    " order by b.serialnumber \n";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = Util.null2String(rs.getString("resourceid"));
                String kqdate = Util.null2String(rs.getString("kqdate"));
                String serialid = Util.null2String(rs.getString("serialid"));
                int serialnumber  = rs.getInt("serialnumber")+1;
                String workbegindate = Util.null2String(rs.getString("workbegindate")).trim();
                String workbegintime = Util.null2String(rs.getString("workbegintime")).trim();
                String workenddate = Util.null2String(rs.getString("workenddate")).trim();
                String workendtime = Util.null2String(rs.getString("workendtime")).trim();
                int workMins = rs.getInt("workMins");
                String signintime = Util.null2String(rs.getString("signintime")).trim();
                String signouttime = Util.null2String(rs.getString("signouttime")).trim();
                int attendanceMins = rs.getInt("attendanceMins");
                String beLateMins = Util.null2String(rs.getString("beLateMins")).trim();
                String graveBeLateMins = Util.null2String(rs.getString("graveBeLateMins")).trim();
                String leaveEarlyMins= Util.null2String(rs.getString("leaveEarlyMins")).trim();
                String graveLeaveEarlyMins= Util.null2String(rs.getString("graveLeaveEarlyMins")).trim();
                String absenteeismMins= Util.null2String(rs.getString("absenteeismMins")).trim();
                String forgotCheckMins = Util.null2String(rs.getString("forgotcheckMins")).trim();
                String forgotBeginWorkCheckMins = Util.null2String(rs.getString("forgotBeginWorkCheckMins")).trim();
                String signinid = Util.null2String(rs.getString("signinid")).trim();
                String signoutid = Util.null2String(rs.getString("signoutid")).trim();
                int leaveMins = rs.getInt("leaveMins");
                String leaveInfo = Util.null2String(rs.getString("leaveInfo"));
                int evectionMins = rs.getInt("evectionMins");
                int outMins = rs.getInt("outMins");

                String tmpkey = resourceid+"|"+kqdate+"|";


                if(serialid.length()>0){
                    if (workbegintime.length() > 0) {
                        signStatusInfo = new HashMap();
                        signStatusInfo.put("workdate",workbegindate);
                        signStatusInfo.put("worktime",workbegintime);
                        signStatusInfo.put("beLateMins",beLateMins);
                        signStatusInfo.put("forgotBeginWorkCheckMins",forgotBeginWorkCheckMins);
                        signStatusInfo.put("graveBeLateMins",graveBeLateMins);
                        signStatusInfo.put("absenteeismMins",absenteeismMins);
                        signStatusInfo.put("leaveMins",leaveMins);
                        signStatusInfo.put("leaveInfo",leaveInfo);
                        signStatusInfo.put("evectionMins",evectionMins);
                        signStatusInfo.put("outMins",outMins);
                        signStatusInfo.put("isneedcal",isneedcal);

                        data.put(tmpkey+"signintime"+serialnumber, signintime.length()==0?SystemEnv.getHtmlLabelName(25994, user.getLanguage()):signintime);
                        data.put(tmpkey+"signinstatus"+serialnumber, com.engine.kq.biz.KQReportBiz.getSignStatus(signStatusInfo,user,"on"));
                    }

                    if (workendtime.length() > 0) {
                        signStatusInfo = new HashMap();
                        signStatusInfo.put("workdate",workenddate);
                        signStatusInfo.put("worktime",kqTimesArrayComInfo.turn48to24Time(workendtime));
                        signStatusInfo.put("leaveEarlyMins",leaveEarlyMins);
                        signStatusInfo.put("graveLeaveEarlyMins",graveLeaveEarlyMins);
                        signStatusInfo.put("forgotCheckMins",forgotCheckMins);
                        signStatusInfo.put("forgotBeginWorkCheckMins",forgotBeginWorkCheckMins);
                        signStatusInfo.put("absenteeismMins",absenteeismMins);
                        signStatusInfo.put("leaveMins",leaveMins);
                        signStatusInfo.put("leaveInfo",leaveInfo);
                        signStatusInfo.put("evectionMins",evectionMins);
                        signStatusInfo.put("outMins",outMins);
                        signStatusInfo.put("isneedcal",isneedcal);

                        data.put(tmpkey+"signouttime"+serialnumber, signouttime.length()==0?SystemEnv.getHtmlLabelName(25994, user.getLanguage()):signouttime);
                        data.put(tmpkey+"signoutstatus"+serialnumber, com.engine.kq.biz.KQReportBiz.getSignStatus(signStatusInfo,user,"off"));
                    }
                }else{
                    if(workMins>0){
                        //弹性工时打卡时间取自签到签退数据
                    }
                    signStatusInfo = new HashMap();
                    signStatusInfo.put("leaveMins",leaveMins);
                    signStatusInfo.put("leaveInfo",leaveInfo);
                    signStatusInfo.put("evectionMins",evectionMins);
                    signStatusInfo.put("outMins",outMins);
                    signStatusInfo.put("isneedcal",isneedcal);

                    if(signinid.length() > 0){
                        data.put(tmpkey+"signintime"+serialnumber, signintime.length()==0?SystemEnv.getHtmlLabelName(25994, user.getLanguage()):signintime);
                        data.put(tmpkey+"signinstatus"+serialnumber, com.engine.kq.biz.KQReportBiz.getSignStatus(signStatusInfo,user,"on"));
                        if(signoutid.length() > 0){
                            data.put(tmpkey+"signouttime"+serialnumber, signouttime.length()==0?SystemEnv.getHtmlLabelName(25994, user.getLanguage()):signouttime);
                            data.put(tmpkey+"signoutstatus"+serialnumber, com.engine.kq.biz.KQReportBiz.getSignStatus(signStatusInfo,user,"off"));
                        }
                    }else{
                        data.put(tmpkey+"signinstatus"+serialnumber, com.engine.kq.biz.KQReportBiz.getSignStatus(signStatusInfo,user,"on"));
                    }
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return data;
    }

    /**
     * 获取每日销假数据
     * @param params
     * @param user
     * @return
     */
    public Map<String,Object> getDailyFlowLeaveBackData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, newleavetype, durationrule, sum(duration) as val,belongdate from hrmresource a, "+KqSplitFlowTypeEnum.LEAVEBACK.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere	+
                    " group by resourceid, newleavetype, durationrule,belongdate ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String belongdate = rs.getString("belongdate");
                String newleavetype = rs.getString("newleavetype");
                String durationrule = rs.getString("durationrule");
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");

                double proportion = Util.getDoubleValue(kqLeaveRulesComInfo.getProportion(newleavetype));
                if(KQUnitBiz.isLeaveHour(newleavetype,kqLeaveRulesComInfo)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|"+belongdate+"|leavebackType_"+newleavetype;
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key,format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取实际加班数据，包括流程，打卡生成的
     * @return
     */
    public Map<String,Object> getFlowOverTimeDataNew(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and a.id in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            int uintType = kqOvertimeRulesBiz.getMinimumUnit();//当前加班单位
            double hoursToDay = kqOvertimeRulesBiz.getHoursToDay();//当前天跟小时计算关系

            String valueField = "";

            sql = " select resourceid,changeType, sum(cast(duration_min as decimal(18,4))) as val,paidLeaveEnable "+
                    " from hrmresource a, kq_flow_overtime b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere+
                    " group by resourceid,changeType,paidLeaveEnable  ";
            rs.execute(sql);
            kqLog.info("getFlowOverTimeDataNew:sql:"+sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String paidLeaveEnable = rs.getString("paidLeaveEnable");
                int changeType =rs.getInt("changeType");//1-节假日、2-工作日、3-休息日
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");
                if(uintType==3 || uintType== 5 || uintType== 6){//按小时计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0)+""));
                }else{//按天计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0*hoursToDay)+""));
                }
                String flowType = "";
                if(changeType==1){
                    flowType = "holidayOvertime";
                }else if(changeType==2){
                    flowType = "workingDayOvertime";
                }else if(changeType==3){
                    flowType = "restDayOvertime";
                }
                if("1".equalsIgnoreCase(paidLeaveEnable)){
                    //1表示关联调休
                    flowType += "_4leave";
                }else{
                    //0表示不关联调休
                    flowType += "_nonleave";
                }
                String key = resourceid+"|"+flowType;
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                if(datas.containsKey(key)){
                    double tmpVal = Util.getDoubleValue(Util.null2String(datas.get(key)),0.0);
                    tmpVal += value;
                    datas.put(key,format(tmpVal));
                }else{
                    datas.put(key,format(value));
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }


    /**
     * 获取加班数据
     * @return
     */
    public Map<String,Object> getFlowOverTimeData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and b.subcompanyid in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and b.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            int uintType = kqOvertimeRulesBiz.getMinimumUnit();//当前加班单位
            double hoursToDay = kqOvertimeRulesBiz.getHoursToDay();//当前天跟小时计算关系

            String valueField = "";
            if(uintType==3 || uintType== 5 || uintType== 6){//按小时计算
                valueField = "sum( case when durationrule='3' then duration else duration*"+hoursToDay+" end) as val";
            }else{//按天计算
                valueField = "sum( case when durationrule='3' then duration/"+hoursToDay+" else duration  end) as val";
            }

            sql = " select resourceid,changeType, " +valueField+
                    " from hrmresource a, "+KqSplitFlowTypeEnum.OVERTIME.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere+
                    " group by resourceid,changeType,durationrule ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                int changeType =rs.getInt("changeType");//1-节假日、2-工作日、3-休息日
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");
                String flowType = "";
                if(changeType==1){
                    flowType = "holidayOvertime";
                }else if(changeType==2){
                    flowType = "workingDayOvertime";
                }else if(changeType==3){
                    flowType = "restDayOvertime";
                }
                String key = resourceid+"|"+flowType;
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                if(datas.containsKey(key)){
                    double tmpVal = Util.getDoubleValue(Util.null2String(datas.get(key)),0.0);
                    tmpVal += value;
                    datas.put(key,format(tmpVal));
                }else{
                    datas.put(key,format(value));
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 获取每日加班数据
     * @return
     */
    public Map<String,Object> getDailyFlowOverTimeData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and a.id in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            int uintType = kqOvertimeRulesBiz.getMinimumUnit();//当前加班单位
            double hoursToDay = kqOvertimeRulesBiz.getHoursToDay();//当前天跟小时计算关系

            String valueField = "";
            if(uintType==3 || uintType== 5 || uintType== 6){//按小时计算
                valueField = "sum( case when durationrule='3' then duration else duration*"+hoursToDay+" end) as val";
            }else{//按天计算
                valueField = "sum( case when durationrule='3' then duration/"+hoursToDay+" else duration  end) as val";
            }

            sql = " select resourceid,changeType,belongdate,paidLeaveEnable, sum(cast(duration_min as decimal(18,4))) as val "+
                    " from hrmresource a, kq_flow_overtime b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere+
                    " group by resourceid,changeType,paidLeaveEnable,belongdate ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String belongdate = rs.getString("belongdate");
                String paidLeaveEnable = rs.getString("paidLeaveEnable");
                int changeType =rs.getInt("changeType");//1-节假日、2-工作日、3-休息日
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");
                if(uintType==3 || uintType== 5 || uintType== 6){//按小时计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0)+""));
                }else{//按天计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0*hoursToDay)+""));
                }
                String flowType = "";
                if(changeType==1){
                    flowType = "holidayOvertime";
                }else if(changeType==2){
                    flowType = "workingDayOvertime";
                }else if(changeType==3){
                    flowType = "restDayOvertime";
                }
                if("1".equalsIgnoreCase(paidLeaveEnable)){
                    //1表示关联调休
                    flowType += "_4leave";
                }else{
                    //0表示不关联调休
                    flowType += "_nonleave";
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(resourceid+"|"+belongdate+"|"+flowType,format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     * 异常冲抵
     * @return
     */
    public Map<String,Object> getFlowOtherData(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            String minimumUnit = "";//单位类型
            double proportion = 0.00;//换算关系

            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and b.resourceid in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            sql = " select resourceid, durationrule, sum(duration) as val from hrmresource a, "+KqSplitFlowTypeEnum.OTHER.getTablename()+" b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' "+sqlWhere+
                    " group by resourceid, durationrule ";
            rs.execute(sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                double value = rs.getDouble("val")<0?0:rs.getDouble("val");
                String durationrule = rs.getString("durationrule");

                if(KQUnitBiz.isLeaveHour(minimumUnit)){//按小时
                    if(!KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value*proportion;
                    }
                }else{//按天
                    if(KQUnitBiz.isLeaveHour(durationrule)){
                        if(proportion>0) value = value/proportion;
                    }
                }

                String key = resourceid+"|leaveDeduction";
                if(datas.containsKey(key)){
                    value += Util.getDoubleValue(Util.null2String(datas.get(key)));
                }
                //df.format 默认是不四舍五入的 0.125这样的就会直接变成0.12了
                df.setMaximumFractionDigits(5);
                datas.put(key, format(value));
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }

    /**
     *日报表数据
     * @param resourceId
     * @param fromDate
     * @param toDate
     * @return
     */
    public Map<String,Object> getDetialDatas(String resourceId,String fromDate, String toDate, User user){
        return getDetialDatas(resourceId,fromDate,toDate,user,new HashMap<String,Object>(),false,0,"0");
    }
    public Map<String,Object> getDetialDatas(String resourceId,String fromDate, String toDate, User user,
                                             Map<String,Object> flowData,boolean isWrap,int uintType,String show_card_source){
        KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
        Map<String,Object> datas = new HashMap<>();
        Map<String,Object> data = null;
        Map<String,Object> tmpdatas = new HashMap<>();
        Map<String,Object> tmpdatass = new HashMap<>();
        Map<String,Object> tmpdata = null;
        Map<String,Object> tmpmap = null;

        Map<String,String> tmpstatusdata = new HashMap<>();
        Map<String,String> tmpstatus = null;
        RecordSet rs = new RecordSet();
        String sql = "";

        //add
        String unit = "小时";
        if(uintType==1 || uintType== 2 || uintType== 4){//按天计算
            unit = "天";
        }
        //kqLog.info("detail.flowdata="+JSONObject.toJSONString(flowData));

        try {
            sql = " select resourceid, kqdate, workMins,attendanceMins,signindate,signintime,signoutdate,signouttime,signinid,signoutid, belatemins, graveBeLateMins, leaveearlymins, graveLeaveEarlyMins, absenteeismmins, forgotcheckMins, forgotBeginWorkCheckMins, "+
                    " leaveMins,leaveInfo,evectionMins,outMins " +
                    " from kq_format_detail " +
                    " where resourceid = ? and kqdate>=? and kqdate<=? "+
                    " order by resourceid, kqdate, serialnumber  ";
            rs.executeQuery(sql,resourceId, fromDate,toDate);
            while (rs.next()) {
                String key = rs.getString("resourceid") + "|" + rs.getString("kqdate");
                int workMins = rs.getInt("workMins");

                String attendanceMins = rs.getString("attendanceMins");
//        String chuqin = "出勤："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(attendanceMins) / 60.0)))+"小时";
                String overtimekey = key+"|overtime";
                String overtime = Util.null2String(flowData.get(overtimekey));
                boolean hasovertime = Util.getDoubleValue(overtime)>0;
                overtime = hasovertime?(SystemEnv.getHtmlLabelName(6151, user.getLanguage())+"："+overtime+unit):"";//显示加班

                String signinid = "";
                String signoutid = "";
                String signintime = "";
                String signouttime = "";
                if("1".equals(show_card_source)){
                    String nosign = SystemEnv.getHtmlLabelName(25994, user.getLanguage());//未打卡
                    signinid = Util.null2String(rs.getString("signinid")).trim();
                    signintime = Util.null2String(rs.getString("signintime")).trim();
                    String tmpin = SystemEnv.getHtmlLabelName(21974, user.getLanguage())+"：";
                    if(signinid.length()>0){
                        String signinfrom = Util.null2String(flowData.get(signinid));
                        signintime = tmpin+signintime+" "+signinfrom;
                    }else{
                        signintime = tmpin+nosign;
                    }
//          signintime = isWrap?"\r\n"+signintime:"<br/>"+signintime;

                    signoutid = Util.null2String(rs.getString("signoutid")).trim();
                    signouttime = Util.null2String(rs.getString("signouttime")).trim();
                    String tmpout = SystemEnv.getHtmlLabelName(21975, user.getLanguage())+"：";
                    if(signoutid.length()>0){
                        String signoutfrom = Util.null2String(flowData.get(signoutid));
                        signouttime = tmpout+signouttime+" "+signoutfrom;
                    }else{
                        signouttime = tmpout+nosign;
                    }
                    signouttime = isWrap?"\r\n"+signouttime:"<br/>"+signouttime;
                }

                int beLateMins = rs.getInt("beLateMins");
                int leaveEarlyMins = rs.getInt("leaveEarlyMins");
                int graveBeLateMins = rs.getInt("graveBeLateMins");
                int absenteeismMins = rs.getInt("absenteeismMins");
                int graveLeaveEarlyMins = rs.getInt("graveLeaveEarlyMins");
                int forgotCheckMins = rs.getInt("forgotCheckMins");
                int forgotBeginWorkCheckMins = rs.getInt("forgotBeginWorkCheckMins");
                int leaveMins = rs.getInt("leaveMins");
                String leaveInfo = rs.getString("leaveInfo");
                int evectionMins = rs.getInt("evectionMins");
                int outMins = rs.getInt("outMins");
                String text = "";
                String tmptext ="";
                String flag ="true";
                if(datas.get(key)==null){
                    data = new HashMap<>();
                }else{
                    data = (Map<String,Object>)datas.get(key);
                    tmptext = Util.null2String(data.get("text"));
                }
                tmpdata = new HashMap<>();
                if(tmpdatas.get(key)!=null){
                    tmpmap = (Map<String,Object>)tmpdatas.get(key);
                    flag = Util.null2String(tmpmap.get("text"));
                }

                String yichang ="";
                if(tmpstatusdata.get(key)!=null){
                    yichang = Util.null2String(tmpstatusdata.get(key));
                }
                String sign ="";
                String signkey = key+"|text";
                if(tmpstatusdata.get(signkey)!=null){
                    sign = Util.null2String(tmpstatusdata.get(signkey));
                }

                if (workMins<=0) {
                    if(text.length()>0) text +=" ";
                    text += SystemEnv.getHtmlLabelName(26593, user.getLanguage());
                    //休息日处理
                    if(signinid.length()>0){
                        text += (isWrap?"\r\n":"<br/>")+signintime;
                    }
                    if(signoutid.length()>0){
                        text += signouttime;
                    }
                    if(sign.length()>0) sign += isWrap?"\r\n":"<br/>";
                    sign += text;
                } else {
                    //处理打卡数据==================
                    if(text.length()>0) text+= isWrap?"\r\n":"<br/>";
                    text += signintime;
                    text += signouttime;
                    if(sign.length()>0) sign+= isWrap?"\r\n":"<br/>";
                    sign += text;
                    //处理打卡数据==================

                    if (absenteeismMins > 0) {//旷工
                        if(text.length()>0) text+=" ";
                        text += SystemEnv.getHtmlLabelName(20085, user.getLanguage());
//            text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+absenteeismMins) / 60.0)))+"小时";
                        if(yichang.indexOf(SystemEnv.getHtmlLabelName(20085, user.getLanguage()))==-1){
                            if(yichang.length()>0) yichang+= isWrap?"\r\n":"<br/>";
                            yichang += SystemEnv.getHtmlLabelName(20085, user.getLanguage());
                        }
                    }else {
                        if (beLateMins > 0) {//迟到
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(20081, user.getLanguage());
//              text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+beLateMins) / 60.0)))+"小时";
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(20081, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(20081, user.getLanguage());
                            }
                        }
                        if (graveBeLateMins > 0) {//严重迟到
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(500546, user.getLanguage());
//              text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+graveBeLateMins) / 60.0)))+"小时";
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(500546, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(500546, user.getLanguage());
                            }
                        }
                        if (leaveEarlyMins > 0) {//早退
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(20082, user.getLanguage());
//              text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+leaveEarlyMins) / 60.0)))+"小时";
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(20082, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(20082, user.getLanguage());
                            }
                        }
                        if (graveLeaveEarlyMins > 0) {//严重早退
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(500547, user.getLanguage());
//              text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+graveLeaveEarlyMins) / 60.0)))+"小时";
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(500547, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(500547, user.getLanguage());
                            }
                        }
                        if (forgotCheckMins > 0) {//漏签
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(20086, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                            }
                        }
                        if (forgotBeginWorkCheckMins > 0) {//漏签
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                            if(yichang.indexOf(SystemEnv.getHtmlLabelName(20086, user.getLanguage()))==-1) {
                                if (yichang.length() > 0) yichang += isWrap?"\r\n":"<br/>";
                                yichang += SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                            }
                        }
                    }
                }
                if (leaveMins > 0) {//请假
                    Map<String,Object> jsonObject = null;
                    if(leaveInfo.length()>0){
                        jsonObject = JSON.parseObject(leaveInfo);
                        for (Entry<String,Object> entry : jsonObject.entrySet()) {
                            String newLeaveType = entry.getKey();
                            String tmpLeaveMins = Util.null2String(entry.getValue());
                            if(text.indexOf(kqLeaveRulesComInfo.getLeaveName(newLeaveType))==-1){
                                if (text.length() > 0) text += " ";
                                //text += kqLeaveRulesComInfo.getLeaveName(newLeaveType)+tmpLeaveMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                                text += Util.formatMultiLang( kqLeaveRulesComInfo.getLeaveName(newLeaveType),""+user.getLanguage());
//                text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+leaveMins) / 60.0)))+"小时";
                                if(yichang.length()>0) yichang+= isWrap?"\r\n":"<br/>";
                                yichang += Util.formatMultiLang( kqLeaveRulesComInfo.getLeaveName(newLeaveType),""+user.getLanguage());
                            }
                        }
                    }else{
                        if(text.indexOf(SystemEnv.getHtmlLabelName(670, user.getLanguage()))==-1) {
                            if (text.length() > 0) text += " ";
                            text += SystemEnv.getHtmlLabelName(670, user.getLanguage());
                        }
                    }
                }
                if (evectionMins > 0) {//出差
                    if(text.indexOf(SystemEnv.getHtmlLabelName(20084, user.getLanguage()))==-1) {
                        if (text.length() > 0) text += " ";
                        text += SystemEnv.getHtmlLabelName(20084, user.getLanguage());
//            text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+evectionMins) / 60.0)))+"小时";
                        if(yichang.length()>0) yichang+= isWrap?"\r\n":"<br/>";
                        yichang += SystemEnv.getHtmlLabelName(20084, user.getLanguage());
                    }
                }
                if (outMins > 0) {//公出
                    if(text.indexOf(SystemEnv.getHtmlLabelName(24058, user.getLanguage()))==-1) {
                        if (text.length() > 0) text += " ";
                        text += SystemEnv.getHtmlLabelName(24058, user.getLanguage());
//            text += "："+KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(""+outMins) / 60.0)))+"小时";
                        if(yichang.length()>0) yichang+= isWrap?"\r\n":"<br/>";
                        yichang += SystemEnv.getHtmlLabelName(24058, user.getLanguage());
                    }
                }

                if(text.length()==0) {
                    text = "√";
                }else{
                    flag = "false";//有其他的异常状态,则表示为false，不需要处理直接全部显示即可
                }
                text += overtime;


                //需要处理下打卡时间和异常状态显示的顺序--start
                tmpstatusdata.put(key, yichang);
                tmpstatusdata.put(signkey, sign);
                boolean hasyichang = tmpstatusdata.get(key).length()>0;
                if(tmptext.length()>0){
//          text = tmpstatusdata.get(signkey)+(isWrap?"\r\n":"<br/>")+tmpstatusdata.get(key)+(isWrap?"\r\n":"<br/>"+overtime);
                    text = tmpstatusdata.get(signkey);
                    if(hasyichang){
                        if(text.length()>0){
                            text += (isWrap?"\r\n":"<br/>")+tmpstatusdata.get(key);
                        }else{
                            text += tmpstatusdata.get(key);
                        }
                    }
                    if(hasovertime){
                        text += (isWrap?"\r\n":"<br/>")+overtime;
                    }
                }else{
                    text = tmpstatusdata.get(signkey);
                    if(hasyichang){
                        if(text.length()>0){
                            text += (isWrap?"\r\n":"<br/>")+tmpstatusdata.get(key);
                        }else{
                            text += tmpstatusdata.get(key);
                        }
                    }
                    if(hasovertime){
                        text += (isWrap?"\r\n":"<br/>")+overtime;
                    }
                }
                //需要处理下打卡时间和异常状态显示的顺序--end
                tmpdatass.put(key, (isWrap?"\r\n":"<br/>")+overtime);
//        text = tmptext.length()>0?tmptext+" "+text:text;//显示所有的状态
                data.put("text", text);
                datas.put(key, data);

                //add
                tmpdata.put("text", flag);
                tmpdatas.put(key, tmpdata);
                //end
            }
            //全部搞一遍
            if(tmpdatas != null){
//        writeLog(n+">>>tmpdatas="+JSONObject.toJSONString(tmpdatas));
                Map<String,Object> data1 = null;
                for(Entry<String,Object> entry : tmpdatas.entrySet()){
                    String mapKey = Util.null2String(entry.getKey());
                    Map<String,Object> mapValue = (Map<String,Object>)entry.getValue();
                    String flag = Util.null2String(mapValue.get("text"));
                    if("true".equals(flag)){//需要加工的数据
                        String  overtime = String.valueOf(tmpdatass.get(mapKey));
                        data1 = new HashMap<>();
                        data1.put("text", "√"+overtime);
                        datas.put(mapKey, data1);
                    }
                }
//        writeLog("datas="+JSONObject.toJSONString(datas));
            }
        }catch (Exception e){
            writeLog(e);
        }
        // 最后针对数据再处理一遍，不然出现2023-01-02: "休息" 形式的错误，导致页面记载报错，应该是2023-01-01: {text: "休息"}格式
        boolean isEnd = false;
        for(String currentDate = fromDate; !isEnd;) {
            if (currentDate.equals(toDate)) isEnd = true;
            String dailyValue = Util.null2String(datas.get(currentDate));
            if(!"".equals(dailyValue) && !dailyValue.contains("text")) {
                Map<String,Object> innerMap2 = new HashMap<>();
                innerMap2.put("text", dailyValue);
                datas.put(currentDate, innerMap2);
            }
            currentDate = DateUtil.addDate(currentDate, 1);
        }

        return datas;
    }

//  public String getSignStatus(Map<String,Object> data, User user){
//    String result = "";
//    String signtype = Util.null2String(data.get("signtype"));//上班 下班
//    String worktime = Util.null2String(data.get("worktime"));//工作时间
//    String signtime = Util.null2String(data.get("signtime"));//签到时间
//    String abnormalMins = Util.null2String(data.get("abnormalMins"));//异常分钟数
//    String forgotCheck = Util.null2String(data.get("forgotCheck"));//漏签
//    if(worktime.length()>0){
//      if(signtime.length()>0){
//        if(Util.getDoubleValue(abnormalMins)>0){
//          result =SystemEnv.getHtmlLabelName(signtype.equals("1")?20081:20082, user.getLanguage())+abnormalMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());//迟到 早退
//        }else{
//          result =SystemEnv.getHtmlLabelName(225, user.getLanguage());//正常
//        }
//      }else if(forgotCheck.equals("1")){
//        result =SystemEnv.getHtmlLabelName(20086, user.getLanguage());//漏签
//      }
//    }else{
//      result = "";
//    }
//
//    return result;
//  }

    public int getSerialCount(String resourceId, String fromDate, String toDate, String serialId){
        RecordSet rs = new RecordSet();
        String sql = "";
        int serialCount = 0;
        try{
            sql = "select count(1) from hrmresource a, kq_format_total b where a.id= b.resourceid and b.resourceid = ? and b.kqdate >=? and b.kqdate <=? and b.serialId = ? ";
            rs.executeQuery(sql,resourceId,fromDate,toDate, serialId);
            if(rs.next()){
                serialCount = rs.getInt(1);
            }
        }catch (Exception e){
            writeLog(e);
        }
        return serialCount;
    }

    public static String getSignStatus(Map<String,Object> signInfo, User user){
        return getSignStatus(signInfo,user,"");
    }

    public static String getSignStatus(Map<String,Object> signInfo, User user,String onOrOff){
        KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
        String text = "";
        String isneedcal = Util.null2String(signInfo.get("isneedcal"));
        String workdate = Util.null2String(signInfo.get("workdate"));
        String worktime = Util.null2String(signInfo.get("worktime"));
        if(!new KQFormatBiz().needCal(workdate,worktime,isneedcal)) {//还未到时间无需计算
            return text;
        }
        int absenteeismMins = Util.getIntValue(Util.null2String(signInfo.get("absenteeismMins")));
        int beLateMins = Util.getIntValue(Util.null2String(signInfo.get("beLateMins")));
        int graveBeLateMins = Util.getIntValue(Util.null2String(signInfo.get("graveBeLateMins")));
        int leaveEarlyMins = Util.getIntValue(Util.null2String(signInfo.get("leaveEarlyMins")));
        int graveLeaveEarlyMins = Util.getIntValue(Util.null2String(signInfo.get("graveLeaveEarlyMins")));
        int forgotCheckMins = Util.getIntValue(Util.null2String(signInfo.get("forgotCheckMins")));
        int forgotBeginWorkCheckMins = Util.getIntValue(Util.null2String(signInfo.get("forgotBeginWorkCheckMins")));
        int leaveMins = Util.getIntValue(Util.null2String(signInfo.get("leaveMins")));
        String leaveInfo = Util.null2String(signInfo.get("leaveInfo"));
        int evectionMins = Util.getIntValue(Util.null2String(signInfo.get("evectionMins")));
        int outMins = Util.getIntValue(Util.null2String(signInfo.get("outMins")));

        if(worktime.length()>0){
            if (absenteeismMins > 0) {//旷工
                text = SystemEnv.getHtmlLabelName(20085, user.getLanguage())+absenteeismMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
            }else {
                if (beLateMins > 0) {//迟到
                    text = SystemEnv.getHtmlLabelName(20081, user.getLanguage())+beLateMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                }
                if (graveBeLateMins > 0) {//严重迟到
                    text = SystemEnv.getHtmlLabelName(	500546, user.getLanguage())+graveBeLateMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                }
                if (leaveEarlyMins > 0) {//早退
                    text = SystemEnv.getHtmlLabelName(20082, user.getLanguage())+leaveEarlyMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                }
                if (graveLeaveEarlyMins > 0) {//严重早退
                    text = SystemEnv.getHtmlLabelName(500547, user.getLanguage())+graveLeaveEarlyMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                }
                if (forgotCheckMins > 0) {//漏签
                    text = SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                }
                if(onOrOff.length() > 0 && "on".equalsIgnoreCase(onOrOff)){
                    if (forgotBeginWorkCheckMins > 0) {//漏签
                        text = SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                    }
                }
            }

            if (leaveMins > 0) {//请假
                Map<String,Object> jsonObject = null;
                if(leaveInfo.length()>0){
                    jsonObject = JSON.parseObject(leaveInfo);
                    for (Entry<String,Object> entry : jsonObject.entrySet()) {
                        String newLeaveType = entry.getKey();
                        String tmpLeaveMins = Util.null2String(entry.getValue());
                        if(text.indexOf(kqLeaveRulesComInfo.getLeaveName(newLeaveType))==-1){
                            if (text.length() > 0) text += " ";
                            //text += kqLeaveRulesComInfo.getLeaveName(newLeaveType)+tmpLeaveMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                            text += Util.formatMultiLang(kqLeaveRulesComInfo.getLeaveName(newLeaveType),""+user.getLanguage());
                        }
                    }
                }else{
                    if(text.indexOf(SystemEnv.getHtmlLabelName(670, user.getLanguage()))==-1) {
                        if (text.length() > 0) text += " ";
                        text += SystemEnv.getHtmlLabelName(670, user.getLanguage());
                    }
                }
            }
            if (evectionMins > 0) {//出差
                if(text.indexOf(SystemEnv.getHtmlLabelName(20084, user.getLanguage()))==-1) {
                    if (text.length() > 0) text += " ";
                    text += SystemEnv.getHtmlLabelName(20084, user.getLanguage());
                }
            }
            if (outMins > 0) {//公出
                if(text.indexOf(SystemEnv.getHtmlLabelName(24058, user.getLanguage()))==-1) {
                    if (text.length() > 0) text += " ";
                    text += SystemEnv.getHtmlLabelName(24058, user.getLanguage());
                }
            }
            if(text.equals("")){
                boolean needCal = new KQFormatBiz().needCal(workdate,worktime);
                text = needCal?SystemEnv.getHtmlLabelName(225, user.getLanguage()):"";
            }
        }else{
            if (leaveMins > 0) {//请假
                Map<String,Object> jsonObject = null;
                if(leaveInfo.length()>0){
                    jsonObject = JSON.parseObject(leaveInfo);
                    for (Entry<String,Object> entry : jsonObject.entrySet()) {
                        String newLeaveType = entry.getKey();
                        String tmpLeaveMins = Util.null2String(entry.getValue());
                        if(text.indexOf(kqLeaveRulesComInfo.getLeaveName(newLeaveType))==-1){
                            if (text.length() > 0) text += " ";
                            //text += kqLeaveRulesComInfo.getLeaveName(newLeaveType)+tmpLeaveMins+SystemEnv.getHtmlLabelName(15049, user.getLanguage());
                            text += Util.formatMultiLang(kqLeaveRulesComInfo.getLeaveName(newLeaveType));
                        }
                    }
                }else{
                    if(text.indexOf(SystemEnv.getHtmlLabelName(670, user.getLanguage()))==-1) {
                        if (text.length() > 0) text += " ";
                        text += SystemEnv.getHtmlLabelName(670, user.getLanguage());
                    }
                }
            }
            if (evectionMins > 0) {//出差
                if(text.indexOf(SystemEnv.getHtmlLabelName(20084, user.getLanguage()))==-1) {
                    if (text.length() > 0) text += " ";
                    text += SystemEnv.getHtmlLabelName(20084, user.getLanguage());
                }
            }
            if (outMins > 0) {//公出
                if(text.indexOf(SystemEnv.getHtmlLabelName(24058, user.getLanguage()))==-1) {
                    if (text.length() > 0) text += " ";
                    text += SystemEnv.getHtmlLabelName(24058, user.getLanguage());
                }
            }
        }
        return text;
    }

    /**
     * 获取打卡状态(不包含具体分钟数)
     * @param signInfo
     * @param user
     * @param onOrOff
     * @return
     */
    public static String getSignStatus2(Map<String, Object> signInfo, User user, String onOrOff) {
        KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
        String text = "";
        String worktime = Util.null2String(signInfo.get("worktime"));
        int absenteeismMins = Util.getIntValue(Util.null2String(signInfo.get("absenteeismMins")));
        int beLateMins = Util.getIntValue(Util.null2String(signInfo.get("beLateMins")));
        int graveBeLateMins = Util.getIntValue(Util.null2String(signInfo.get("graveBeLateMins")));
        int leaveEarlyMins = Util.getIntValue(Util.null2String(signInfo.get("leaveEarlyMins")));
        int graveLeaveEarlyMins = Util.getIntValue(Util.null2String(signInfo.get("graveLeaveEarlyMins")));
        int forgotCheckMins = Util.getIntValue(Util.null2String(signInfo.get("forgotCheckMins")));
        int forgotBeginWorkCheckMins = Util.getIntValue(Util.null2String(signInfo.get("forgotBeginWorkCheckMins")));
        int leaveMins = Util.getIntValue(Util.null2String(signInfo.get("leaveMins")));
        String leaveInfo = Util.null2String(signInfo.get("leaveInfo"));
        int evectionMins = Util.getIntValue(Util.null2String(signInfo.get("evectionMins")));
        int outMins = Util.getIntValue(Util.null2String(signInfo.get("outMins")));

        if (worktime.length() > 0) {
            if (absenteeismMins > 0) {//旷工
                text = SystemEnv.getHtmlLabelName(20085, user.getLanguage());
            } else {
                if (beLateMins > 0) {//迟到
                    text = SystemEnv.getHtmlLabelName(20081, user.getLanguage());
                }
                if (graveBeLateMins > 0) {//严重迟到
                    text = SystemEnv.getHtmlLabelName(500546, user.getLanguage());
                }
                if (leaveEarlyMins > 0) {//早退
                    text = SystemEnv.getHtmlLabelName(20082, user.getLanguage());
                }
                if (graveLeaveEarlyMins > 0) {//严重早退
                    text = SystemEnv.getHtmlLabelName(500547, user.getLanguage());
                }
                if (forgotCheckMins > 0) {//漏签
                    text = SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                }
                if (onOrOff.length() > 0 && "on".equalsIgnoreCase(onOrOff)) {
                    if (forgotBeginWorkCheckMins > 0) {//漏签
                        text = SystemEnv.getHtmlLabelName(20086, user.getLanguage());
                    }
                }
            }
            if (text.equals("") && leaveMins <= 0 && evectionMins <= 0 && outMins <= 0) {
                text = SystemEnv.getHtmlLabelName(225, user.getLanguage());
            }
        }
        return text;
    }

    public static boolean getShowFlowText(String leaveInfo,String onOrOff){
        Map<String,Object> jsonObject = null;
        jsonObject = JSON.parseObject(leaveInfo);
        String flow_signtype = "";
        for (Entry<String,Object> entry : jsonObject.entrySet()) {
            String tmpSignType = Util.null2String(entry.getValue());
            flow_signtype += ","+tmpSignType;
        }
        if(flow_signtype.length() == 0){
            return true;
        }
        return isShowFlowText(flow_signtype,onOrOff);
    }

    public static boolean isShowFlowText(String flow_signtype,String onOrOff){
        boolean showFlowText = true;

        if(flow_signtype.length() > 0){
            flow_signtype = flow_signtype.substring(1);
            if("on".equalsIgnoreCase(onOrOff)){
                if((","+flow_signtype+",").indexOf(",1,") > -1){
                    //抵扣了上班卡
                }else{
                    //上班状态，但是没有上班抵扣流程
                    showFlowText = false;
                }
            }
            if("off".equalsIgnoreCase(onOrOff)){
                if((","+flow_signtype+",").indexOf(",2,") > -1){
                    //抵扣了下班卡
                }else{
                    //下班状态，但是没有下班抵扣流程
                    showFlowText = false;
                }
            }
        }
        return showFlowText;
    }

    public static int getPageSize(String pageSize, String pageUid, int userid){
        String sql = "";
        RecordSet rs = new RecordSet();
        int iPageSize= Util.getIntValue(pageSize,10);
        if(iPageSize<10)iPageSize=10;
        try{
            if(pageSize.length()>0){
                boolean flag = false;
                sql = "select count(1) from ecology_pagesize where pageid = '"+pageUid+"' and userid ="+userid;
                rs.executeQuery(sql);
                if(rs.next()){
                    if(rs.getInt(1)>0){
                        flag = true;
                    }
                }
                if(flag){
                    sql = "update ecology_pagesize set pagesize ="+pageSize+"  where pageid = '"+pageUid+"' and userid ="+userid;
                    rs.executeUpdate(sql);
                }else{
                    sql = "insert into ecology_pagesize (pageid,pagesize,userid) values ('"+pageUid+"',"+pageSize+","+userid+")";
                    rs.executeUpdate(sql);
                }
            }else{
                sql = "select pageSize from ecology_pagesize where pageid = '"+pageUid+"' and userid ="+userid;
                rs.executeQuery(sql);
                if(rs.next()){
                    iPageSize = rs.getInt("pageSize");
                }
            }
        }catch (Exception e){
            new BaseBean().writeLog("KQReportBiz.getPageSize"+e);
        }

        return iPageSize;
    }

    //经常遇到申请变更流程或销假流程提示未归档，不胜其烦，所以搞一下
    public void reflow(String requestid){
        try {
            requestid = Util.null2String(requestid);
            String workflowid = "";
            String currentnodetype = "";
            if(requestid.length() == 0){
                return;
            }
            RecordSet rs2 = new RecordSet();
            String sql = "select requestid,workflowid,currentnodetype from workflow_requestbase where requestid = '"+requestid+"'";
            rs2.executeQuery(sql);
            if(rs2.next()){
                workflowid = Util.null2String(rs2.getString("workflowid"));
                currentnodetype = Util.null2String(rs2.getString("currentnodetype"));
            }

            boolean isForce1 = false;
            boolean isUpgrade1 = false;
            if(requestid.length() == 0){
                return ;
            }
            if(workflowid.length() == 0){
                return ;
            }
            if(!"3".equals(currentnodetype)){
                return ;
            }
            long start = System.currentTimeMillis();

            KQFlowActiontBiz kqFlowActiontBiz = new KQFlowActiontBiz();
            RecordSet rs = new RecordSet();
            RecordSet rs1 = new RecordSet();
            String proc_set_sql = "select * from kq_att_proc_set where field001 = ?  ";
            rs.executeQuery(proc_set_sql, workflowid);
            if(rs.next()){
                String proc_set_id = rs.getString("id");
                //得到这个考勤流程设置是否使用明细
                String usedetails = rs.getString("usedetail");

                int kqtype = Util.getIntValue(rs.getString("field006"));

                Map<String, String> map = new HashMap<String, String>();
                if(Util.getIntValue(requestid) > 0){
                    map.put("requestId", "and t.requestId = " + requestid);
                }
                String tablename = "";
                if(kqtype == KqSplitFlowTypeEnum.LEAVE.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.LEAVE.getTablename();
                }else if(kqtype == KqSplitFlowTypeEnum.EVECTION.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.EVECTION.getTablename();
                }else if(kqtype == KqSplitFlowTypeEnum.OUT.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.OUT.getTablename();;
                }else if(kqtype == KqSplitFlowTypeEnum.OVERTIME.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.OVERTIME.getTablename();;
                    return;
                }else if(kqtype == KqSplitFlowTypeEnum.SHIFT.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.SHIFT.getTablename();;
                }else if(kqtype == KqSplitFlowTypeEnum.OTHER.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.OTHER.getTablename();;
                }else if(kqtype == KqSplitFlowTypeEnum.CARD.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.CARD.getTablename();;
                    return;
                }else if(kqtype == KqSplitFlowTypeEnum.LEAVEBACK.getFlowtype()){
                    tablename = KqSplitFlowTypeEnum.LEAVEBACK.getTablename();;
                }else{
                    return;
                }
                if(null != tablename && tablename.length() > 0){
                    String tmpsql = "select * from "+tablename+" where requestId="+requestid;
                    rs1.executeQuery(tmpsql);
                    if(rs1.next()){
                        return;//表示已经产生了拆分数据
                    }
                }

                //先根据requestid删除中间表里的数据，再做啥插入操作
                if(kqtype == KqSplitFlowTypeEnum.OVERTIME.getFlowtype()) {
                    return;//加班就不搞了，有遇到用户销假选择错误流程，选择了加班流程导致这里reflow一次，多余生成了调休
                }else{
                    String delSql = "delete from "+tablename+" where requestid = "+requestid;
                    rs1.executeUpdate(delSql);
                    Map<String,String> result = kqFlowActiontBiz.handleKQFlowAction(proc_set_id, usedetails, Util.getIntValue(requestid), kqtype, Util.getIntValue(workflowid), isForce1,isUpgrade1,map);
                }

            }
            long end = System.currentTimeMillis();
        }catch (Exception e){
            e.printStackTrace();;
        }
    }


    //add
    public Map<String,Object> getOverTime(Map<String,Object> params, User user){
        Map<String,Object> datas = new HashMap<>();;
        RecordSet rs = new RecordSet();
        String sql = "";
        String sqlWhere = " ";
        try{
            KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
            String fromDate = Util.null2String(jsonObj.get("fromDate"));
            String toDate = Util.null2String(jsonObj.get("toDate"));
            String typeselect =Util.null2String(jsonObj.get("typeselect"));
            if(typeselect.length()==0)typeselect = "3";
            if(!typeselect.equals("") && !typeselect.equals("0")&& !typeselect.equals("6")){
                if(typeselect.equals("1")){
                    fromDate = TimeUtil.getCurrentDateString();
                    toDate = TimeUtil.getCurrentDateString();
                }else{
                    fromDate = TimeUtil.getDateByOption(typeselect,"0");
                    toDate = TimeUtil.getDateByOption(typeselect,"1");
                }
            }
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
            String departmentId = Util.null2String(jsonObj.get("departmentId"));
            String resourceId = Util.null2String(jsonObj.get("resourceId"));
            String allLevel = Util.null2String(jsonObj.get("allLevel"));
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));
            if(subCompanyId.length()>0){
                sqlWhere +=" and a.subcompanyid1 in("+subCompanyId+") ";
            }

            if(departmentId.length()>0){
                sqlWhere +=" and a.departmentid in("+departmentId+") ";
            }

            if(resourceId.length()>0){
                sqlWhere +=" and a.id in("+resourceId+") ";
            }

            if(viewScope.equals("4")){//我的下属
                if(allLevel.equals("1")){//所有下属
                    sqlWhere+=" and a.managerstr like '%,"+user.getUID()+",%'";
                }else{
                    sqlWhere+=" and a.managerid="+user.getUID();//直接下属
                }
            }
            if (!"1".equals(isNoAccount)) {
                sqlWhere += " and a.loginid is not null "+(rs.getDBType().equals("oracle")?"":" and a.loginid<>'' ");
            }

            int uintType = Util.getIntValue(Util.null2String(params.get("uintType")));//当前加班单位
            double hoursToDay = Util.getDoubleValue(Util.null2String(params.get("hoursToDay")));//当前天跟小时计算关系

            String valueField = "";

            sql = " select resourceid,belongdate,paidLeaveEnable,duration_min "+
                    " from hrmresource a, kq_flow_overtime b "+
                    " where a.id = b.resourceid and belongdate >='"+fromDate+"' and belongdate <='"+toDate+"' " +sqlWhere+
                    " order by resourceid,belongdate ";
            rs.execute(sql);
//      kqLog.info("getOverTime:sql:"+sql);
            while (rs.next()) {
                String resourceid = rs.getString("resourceid");
                String belongdate = rs.getString("belongdate");
                String paidLeaveEnable = rs.getString("paidLeaveEnable");
//        int changeType =rs.getInt("changeType");//1-节假日、2-工作日、3-休息日
                double value = rs.getDouble("duration_min")<0?0:rs.getDouble("duration_min");
                if(uintType==3 || uintType== 5 || uintType== 6){//按小时计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0)+""));
                }else{//按天计算
                    value = Util.getDoubleValue(KQDurationCalculatorUtil.getDurationRound(value/(60.0*hoursToDay)+""));
                }
                String key = resourceid+"|"+belongdate+"|overtime";
                if(value>0){
                    df.setMaximumFractionDigits(5);
                    if(datas.containsKey(key)){
                        double tmpVal = Util.getDoubleValue(Util.null2String(datas.get(key)),0.0);
                        tmpVal += value;
                        datas.put(key,format(tmpVal));
                    }else{
                        datas.put(key,format(value));
                    }

//          if(datas.containsKey(key)){
//            datas.put(key,"加班");
////            datas.put(key,SystemEnv.getHtmlLabelName(6151, user.getLanguage()));
//          }else{
//            datas.put(key,"加班");
//          }
                }
            }
        }catch (Exception e){
            writeLog(e);
        }
        return datas;
    }
}

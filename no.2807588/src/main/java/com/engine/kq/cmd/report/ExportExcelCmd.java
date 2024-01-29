package com.engine.kq.cmd.report;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.api.customization.qc2304359.KqReportUtil;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.*;
import com.engine.kq.util.ExcelUtil;
import com.engine.kq.util.KQDurationCalculatorUtil;
import com.engine.kq.util.UtilKQ;
import weaver.common.DateUtil;
import weaver.common.StringUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.company.SubCompanyComInfo;
import weaver.hrm.job.JobTitlesComInfo;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.SystemEnv;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

public class ExportExcelCmd extends AbstractCommonCommand<Map<String, Object>> {

  private HttpServletRequest request;
  private HttpServletResponse response;
  private List<String> lsFieldDataKey;

  public ExportExcelCmd(Map<String, Object> params, HttpServletRequest request, HttpServletResponse response, User user) {
    this.user = user;
    this.params = params;
    this.request = request;
    this.response = response;
    this.lsFieldDataKey = new ArrayList<>();
  }

  @Override
  public Map<String, Object> execute(CommandContext commandContext) {
    Map<String, Object> retmap = new HashMap<String, Object>();
    RecordSet rs = new RecordSet();
    String sql = "";
    try {
      SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
      DepartmentComInfo departmentComInfo = new DepartmentComInfo();
      ResourceComInfo resourceComInfo = new ResourceComInfo();
      JobTitlesComInfo jobTitlesComInfo = new JobTitlesComInfo();
      KQLeaveRulesBiz kqLeaveRulesBiz = new KQLeaveRulesBiz();
      KQReportBiz kqReportBiz = new KQReportBiz();

      JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
      String attendanceSerial = Util.null2String(jsonObj.get("attendanceSerial"));
      String fromDate = Util.null2String(jsonObj.get("fromDate"));
      String toDate = Util.null2String(jsonObj.get("toDate"));
      String typeselect = Util.null2String(jsonObj.get("typeselect"));
      if (typeselect.length() == 0) typeselect = "3";
      if (!typeselect.equals("") && !typeselect.equals("0") && !typeselect.equals("6")) {
        if (typeselect.equals("1")) {
          fromDate = TimeUtil.getCurrentDateString();
          toDate = TimeUtil.getCurrentDateString();
        } else {
          fromDate = TimeUtil.getDateByOption(typeselect, "0");
          toDate = TimeUtil.getDateByOption(typeselect, "1");
        }
      }
      //人员状态
      String status = Util.null2String(jsonObj.get("status"));
      String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));
      String departmentId = Util.null2String(jsonObj.get("departmentId"));
      String resourceId = Util.null2String(jsonObj.get("resourceId"));
      String allLevel = Util.null2String(jsonObj.get("allLevel"));
      String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
      String viewScope = Util.null2String(jsonObj.get("viewScope"));
      List<String> showColumns = Util.splitString2List(Util.null2String(jsonObj.get("showColumns")), ",");
      showColumns.add("lastname");
      showColumns.removeIf(showColumn->showColumn.trim().equals(""));

      List<String> tmpShowColumns = new ArrayList<>();
      for(String showColumn:showColumns){
        tmpShowColumns.add(showColumn);
        String cascadekey = "";
        if(showColumn.equals("beLate")){
          cascadekey = "beLateMins";
        }else if(showColumn.equals("leaveEearly")){
          cascadekey = "leaveEarlyMins";
        }else if(showColumn.equals("graveBeLate")){
          cascadekey = "graveBeLateMins";
        }else if(showColumn.equals("graveLeaveEarly")){
          cascadekey = "graveLeaveEarlyMins";
        }else if(showColumn.equals("absenteeism")){
          cascadekey = "absenteeismMins";
        }else if(showColumn.equals("overtime")){
          tmpShowColumns.add("overtime_4leave");
          tmpShowColumns.add("overtime_nonleave");
          tmpShowColumns.add("workingDayOvertime_nonleave");
          tmpShowColumns.add("workingDayOvertime_4leave");
          tmpShowColumns.add("restDayOvertime_nonleave");
          tmpShowColumns.add("restDayOvertime_4leave");
          tmpShowColumns.add("holidayOvertime_4leave");
          tmpShowColumns.add("holidayOvertime_nonleave");
        }
        if(cascadekey.length()>0){
          tmpShowColumns.add(cascadekey);
        }
      }
      showColumns = tmpShowColumns;

      String rightSql = new KQReportBiz().getReportRight("1", "" + user.getUID(), "a");

      LinkedHashMap<String, Object> workbook = new LinkedHashMap<>();
      List<Object> lsSheet = new ArrayList<>();
      Map<String, Object> sheet = null;
      List<Object> titleList = new ArrayList<>();
      Map<String, Object> title = null;
      List<List<Object>> dataList = new ArrayList<>();
      List<Object> data = null;
      List<Map<String, Object>> constraintList = null;

      sheet = new HashMap<>();
      sheet.put("sheetName", SystemEnv.getHtmlLabelName(390351, user.getLanguage()));
      sheet.put("sheetTitle", SystemEnv.getHtmlLabelName(390351, user.getLanguage()));
      boolean isEnd = false;
      Calendar cal = DateUtil.getCalendar();

      List<Map<String, Object>> leaveRules = kqLeaveRulesBiz.getAllLeaveRules();
      Map<String, Object> mapChildColumnInfo = null;
      List<Object> childColumns = null;
      KQReportFieldComInfo kqReportFieldComInfo = new KQReportFieldComInfo();
      while (kqReportFieldComInfo.next()) {
        if (Util.null2String(kqReportFieldComInfo.getParentid()).length() > 0) continue;
        if(kqReportFieldComInfo.getFieldname().equals("kqCalendar"))continue;
        if(KQReportFieldComInfo.cascadekey2fieldname.keySet().contains(kqReportFieldComInfo.getFieldname()))continue;
        if (!kqReportFieldComInfo.getReportType().equals("all") && !kqReportFieldComInfo.getReportType().equals("month"))
          continue;
        if (!showColumns.contains(kqReportFieldComInfo.getFieldname())&&!showColumns.contains(kqReportFieldComInfo.getParentid())) continue;
        if("leave".equalsIgnoreCase(kqReportFieldComInfo.getFieldname())&&leaveRules.size()==0){
          continue;
        }
        title = new HashMap<>();
        String unitType = KQReportBiz.getUnitType(kqReportFieldComInfo, user);
        if(unitType.length()>0){
          title.put("title", SystemEnv.getHtmlLabelNames(kqReportFieldComInfo.getFieldlabel(), user.getLanguage())+ "(" + unitType + ")");
        }else{
          title.put("title", SystemEnv.getHtmlLabelNames(kqReportFieldComInfo.getFieldlabel(), user.getLanguage()));
        }
        title.put("width", 30 * 256);
        this.lsFieldDataKey.add(kqReportFieldComInfo.getFieldname());
        mapChildColumnInfo = this.getChildColumnsInfo(kqReportFieldComInfo.getFieldname(), user);
        childColumns = (List<Object>) mapChildColumnInfo.get("childColumns");
        if (childColumns.size() > 0) {//跨列width取子列的width
          title.put("children", childColumns);
          title.put("colSpan", childColumns.size());
        } else {
          title.put("rowSpan", 3);
        }
        titleList.add(title);

        titleList.addAll(this.getCascadeKeyColumnsInfo(kqReportFieldComInfo.getCascadekey(),user));
      }

      String today = DateUtil.getCurrentDate();
//      if (DateUtil.compDate(today, toDate) > 0) {//结束如期不大于今天
//        toDate = today;
//      }

      if(showColumns.contains("kqCalendar")) {
        childColumns = new ArrayList<>();
        for (String date = fromDate; !isEnd; ) {
          if (date.equals(toDate)) isEnd = true;
          title = new HashMap<>();
          title.put("title", UtilKQ.getWeekDayShort(DateUtil.getWeek(date)-1,user.getLanguage()) +"\r\n"+ DateUtil.geDayOfMonth(date));
          title.put("width", 30 * 256);
          childColumns.add(title);
          cal.setTime(DateUtil.parseToDate(date));
          date = DateUtil.getDate(cal.getTime(), 1);
        }

        title = new HashMap();
        title.put("title", SystemEnv.getHtmlLabelName(386476, user.getLanguage()));
        if (childColumns.size() > 0) {//跨列width取子列的width
          title.put("children", childColumns);
          title.put("colSpan", childColumns.size());
        }
        titleList.add(title);
      }
      sheet.put("titleList", titleList);

      String forgotBeginWorkCheck_field = " sum(b.forgotBeginWorkCheck) ";

      if(rs.getDBType().equalsIgnoreCase("oracle")) {
        forgotBeginWorkCheck_field = " sum(nvl(b.forgotBeginWorkCheck,0))  ";
      }else if((rs.getDBType()).equalsIgnoreCase("mysql")){
        forgotBeginWorkCheck_field = " sum(ifnull(b.forgotBeginWorkCheck,0)) ";
      }else {
        forgotBeginWorkCheck_field = " sum(isnull(b.forgotBeginWorkCheck,0)) ";
      }

      Map<String,Object> definedFieldInfo = new KQFormatBiz().getDefinedField();
      String definedFieldSum = Util.null2String(definedFieldInfo.get("definedFieldSum"));
      String backFields = " a.id,a.lastname,a.workcode,a.dsporder,b.resourceid,a.subcompanyid1 as subcompanyid,a.departmentid,a.jobtitle," +
              " sum(b.workdays) as workdays,sum(b.workMins) as workMins,sum(b.attendancedays) as attendancedays," +
              " sum(b.attendanceMins) as attendanceMins,sum(b.beLate) as beLate,sum(b.beLateMins) as beLateMins, " +
              " sum(b.graveBeLate) as graveBeLate, sum(b.graveBeLateMins) as graveBeLateMins,sum(b.leaveEearly) as leaveEearly," +
              " sum(b.leaveEarlyMins) as leaveEarlyMins, sum(b.graveLeaveEarly) as graveLeaveEarly, " +
              " sum(b.graveLeaveEarlyMins) as graveLeaveEarlyMins,sum(b.absenteeism) as absenteeism, " +
              " sum(b.signdays) as signdays,sum(b.signmins) as signmins, "+
              " sum(b.lunchAllowance) as lunchAllowance, sum(b.dinnerAllowance) as dinnerAllowance,"+"sum(b.midnightAllowance) as midnightAllowance,"+
              " sum(b.absenteeismMins) as absenteeismMins, sum(b.forgotCheck)+"+forgotBeginWorkCheck_field+" as forgotCheck "+(definedFieldSum.length()>0?","+definedFieldSum+"":"");
      if(rs.getDBType().equals("oracle")){
        backFields = 	"/*+ index(kq_format_total IDX_KQ_FORMAT_TOTAL_KQDATE) */ "+backFields;
      }
      String sqlFrom = " from hrmresource a, kq_format_total b where a.id= b.resourceid and b.kqdate >='" + fromDate + "' and b.kqdate <='" + toDate + "'";
      String sqlWhere = rightSql;
      String groupBy = " group by a.id,a.lastname,a.workcode,a.dsporder,b.resourceid,a.subcompanyid1,a.departmentid,a.jobtitle ";
      if (subCompanyId.length() > 0) {
        sqlWhere += " and b.subcompanyid in(" + subCompanyId + ") ";
      }

      if (departmentId.length() > 0) {
        sqlWhere += " and b.departmentid in(" + departmentId + ") ";
      }

      if (resourceId.length() > 0) {
        sqlWhere += " and b.resourceid in(" + resourceId + ") ";
      }

      if (viewScope.equals("4")) {//我的下属
        if (allLevel.equals("1")) {//所有下属
          sqlWhere += " and a.managerstr like '%," + user.getUID() + ",%'";
        } else {
          sqlWhere += " and a.managerid=" + user.getUID();//直接下属
        }
      }
      if (!"1".equals(isNoAccount)) {
        sqlWhere += " and a.loginid is not null " + (rs.getDBType().equals("oracle") ? "" : " and a.loginid<>'' ");
      }

      if(status.length()>0){
        if (!status.equals("8") && !status.equals("9")) {
          sqlWhere += " and a.status = "+status+ "";
        }else if (status.equals("8")) {
          sqlWhere += " and (a.status = 0 or a.status = 1 or a.status = 2 or a.status = 3) ";
        }
      }

      String orderBy = " order by a.dsporder asc, a.lastname asc ";
      String descOrderBy = " order by a.dsporder desc, a.lastname desc ";
      sql = "select " + backFields + sqlFrom + sqlWhere + groupBy + orderBy;

      //System.out.println("start" + DateUtil.getFullDate());
      Map<String, Object> flowData = new KQReportBiz().getFlowData(params, user);
      //System.out.println("end" + DateUtil.getFullDate());
      //==zj qc2304359
      KqReportUtil kqReportUtil = new KqReportUtil();
      new BaseBean().writeLog("==zj==(导出sql)" + JSON.toJSONString(sql));
      rs.execute(sql);
      while (rs.next()) {
        data = new ArrayList<>();
        String id = rs.getString("id");
        for (int fieldDataKeyIdx =0;fieldDataKeyIdx<lsFieldDataKey.size();fieldDataKeyIdx++) {
          String fieldName = lsFieldDataKey.get(fieldDataKeyIdx);
          String fieldid =  KQReportFieldComInfo.field2Id.get(fieldName);
          String fieldValue = "";
          if (fieldName.equals("subcompany")) {
            String tmpSubcompanyId = Util.null2String(rs.getString("subcompanyid"));
            if (tmpSubcompanyId.length() == 0) {
              tmpSubcompanyId = Util.null2String(resourceComInfo.getSubCompanyID(id));
            }
            fieldValue = subCompanyComInfo.getSubCompanyname(tmpSubcompanyId);
          } else if (fieldName.equals("department")) {
            String tmpDepartmentId = Util.null2String(rs.getString("departmentid"));
            if (tmpDepartmentId.length() == 0) {
              tmpDepartmentId = Util.null2String(resourceComInfo.getDepartmentID(id));
            }
            fieldValue = departmentComInfo.getDepartmentname(tmpDepartmentId);
          } else if (fieldName.equals("jobtitle")) {
            String tmpJobtitleId = Util.null2String(rs.getString("jobtitle"));
            if (tmpJobtitleId.length() == 0) {
              tmpJobtitleId = Util.null2String(resourceComInfo.getJobTitle(id));
            }
            fieldValue = jobTitlesComInfo.getJobTitlesname(tmpJobtitleId);
          } else if (fieldName.equals("attendanceSerial")) {
            List<String> serialIds = null;
            if (Util.null2String(jsonObj.get("attendanceSerial")).length() > 0) {
              serialIds = Util.splitString2List(Util.null2String(jsonObj.get("attendanceSerial")), ",");
              for(int i=0;serialIds!=null&&i<serialIds.size();i++){
                data.add(kqReportBiz.getSerialCount(id,fromDate,toDate,serialIds.get(i)));
              }
            }else{
              data.add("");
            }
            continue;
          } else if (fieldName.equals("leave")) {//请假
            List<Map<String, Object>> allLeaveRules = kqLeaveRulesBiz.getAllLeaveRules();
            Map<String, Object> leaveRule = null;
            for (int i = 0; allLeaveRules != null && i < allLeaveRules.size(); i++) {
              leaveRule = allLeaveRules.get(i);
              String flowType = Util.null2String("leaveType_" + leaveRule.get("id"));
              String leaveData = Util.null2String(flowData.get(id + "|" + flowType));
              String flowLeaveBackType = Util.null2String("leavebackType_" + leaveRule.get("id"));
              String leavebackData = Util.null2s(Util.null2String(flowData.get(id + "|" + flowLeaveBackType)), "0.0");
              String b_flowLeaveData = "";
              String flowLeaveData = "";
              try {
                //以防止出现精度问题
                if (leaveData.length() == 0) {
                  leaveData = "0.0";
                }
                if (leavebackData.length() == 0) {
                  leavebackData = "0.0";
                }
                BigDecimal b_leaveData = new BigDecimal(leaveData);
                BigDecimal b_leavebackData = new BigDecimal(leavebackData);
                b_flowLeaveData = b_leaveData.subtract(b_leavebackData).toString();
                if(Util.getDoubleValue(b_flowLeaveData, -1) < 0){
                  b_flowLeaveData = "0.0";
                }
              } catch (Exception e) {
                writeLog("GetKQReportCmd:leaveData" + leaveData + ":leavebackData:" + leavebackData + ":" + e);
              }

              if (b_flowLeaveData.length() > 0) {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(b_flowLeaveData);
              } else {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(Util.null2String(Util.getDoubleValue(leaveData, 0.0) - Util.getDoubleValue(leavebackData, 0.0)));
              }
              data.add(flowLeaveData);
            }
            continue;
          }else if(fieldName.equals("overtime")){
            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|workingDayOvertime_nonleave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("workingDayOvertime_nonleave"))));

            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|restDayOvertime_nonleave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("restDayOvertime_nonleave"))));

            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|holidayOvertime_nonleave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("holidayOvertime_nonleave"))));

            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|workingDayOvertime_4leave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("workingDayOvertime_4leave"))));

            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|restDayOvertime_4leave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("restDayOvertime_4leave"))));

            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id + "|holidayOvertime_4leave")));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("holidayOvertime_4leave"))));

            double workingDayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|workingDayOvertime_4leave")));
            workingDayOvertime_4leave = workingDayOvertime_4leave<0?0:workingDayOvertime_4leave;
            double restDayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|restDayOvertime_4leave")));
            restDayOvertime_4leave = restDayOvertime_4leave<0?0:restDayOvertime_4leave;
            double holidayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|holidayOvertime_4leave")));
            holidayOvertime_4leave = holidayOvertime_4leave<0?0:holidayOvertime_4leave;

            double workingDayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|workingDayOvertime_nonleave")));
            workingDayOvertime_nonleave = workingDayOvertime_nonleave<0?0:workingDayOvertime_nonleave;
            double restDayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|restDayOvertime_nonleave")));
            restDayOvertime_nonleave = restDayOvertime_nonleave<0?0:restDayOvertime_nonleave;
            double holidayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|holidayOvertime_nonleave")));
            holidayOvertime_nonleave = holidayOvertime_nonleave<0?0:holidayOvertime_nonleave;
            fieldValue = KQDurationCalculatorUtil.getDurationRound(String.valueOf(workingDayOvertime_4leave+restDayOvertime_4leave+holidayOvertime_4leave+
                workingDayOvertime_nonleave+restDayOvertime_nonleave+holidayOvertime_nonleave));
            data.add(getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(KQReportFieldComInfo.field2Id.get("overtimeTotal"))));
            continue;
          }else if(fieldName.equals("businessLeave") || fieldName.equals("officialBusiness")){
            String businessLeaveData = Util.null2s(Util.null2String(flowData.get(id+"|"+fieldName)),"0.0");
            String backType = fieldName+"_back";
            String businessLeavebackData = Util.null2s(Util.null2String(flowData.get(id+"|"+backType)),"0.0");
            String businessLeave = "";
            try{
              //以防止出现精度问题
              if(businessLeaveData.length() == 0){
                businessLeaveData = "0.0";
              }
              if(businessLeavebackData.length() == 0){
                businessLeavebackData = "0.0";
              }
              BigDecimal b_businessLeaveData = new BigDecimal(businessLeaveData);
              BigDecimal b_businessLeavebackData = new BigDecimal(businessLeavebackData);
              businessLeave = b_businessLeaveData.subtract(b_businessLeavebackData).toString();
              if(Util.getDoubleValue(businessLeave, -1) < 0){
                businessLeave = "0.0";
              }
            }catch (Exception e){
            }
            fieldValue = KQDurationCalculatorUtil.getDurationRound(businessLeave);
          } else if(Util.null2String(kqReportFieldComInfo.getCascadekey(fieldid)).length()>0){
            fieldValue = Util.formatMultiLang(Util.null2String(rs.getString(fieldName)),""+user.getLanguage());
            data.add(fieldValue);

            List<String> lsCascadekey = Util.splitString2List(kqReportFieldComInfo.getCascadekey(fieldid),",");
            for(int i=0;i<lsCascadekey.size();i++){
              if(Util.null2String(rs.getString(lsCascadekey.get(i))).length()>0){
                fieldid =  KQReportFieldComInfo.field2Id.get(lsCascadekey.get(i));
                //qc2304359 这里判断下是否是迟到和早退
                String kqTypeS = "32month,36month";
                new BaseBean().writeLog("==zj==(导出判断)" + kqTypeS.contains(fieldid) + "   |   " + fieldid);
                if (kqTypeS.contains(fieldid)){
                  fieldValue = kqReportUtil.getAbsence(id,fromDate,toDate,fieldid);
                }else {
                  fieldValue = getFieldValueByUnitType(rs.getString(lsCascadekey.get(i)),kqReportFieldComInfo.getUnittype(fieldid));
                }
              }else{
                fieldValue = "0";
              }
              data.add(fieldValue);
            }
            continue;
          }else if("attendanceRate".equals(fieldName)){
            //计算出勤率
            String attendanceHour = "";
            String workmins = Util.null2String(rs.getString("workmins"));	//应出勤分钟数
            if (!"".equals(Util.null2String(rs.getString("attendanceMins")))){
              int attendanceMins = Integer.parseInt(Util.null2String(rs.getString("attendanceMins"))); //实际出勤分钟数
              attendanceHour = kqReportUtil.attendanceMinsCal(attendanceMins,id,fromDate,toDate);	//获得实际出勤时长
              fieldValue = kqReportUtil.attendanceRateCal(workmins,attendanceHour);	//出勤率
            }else {
              fieldValue = "0%";
            }

          }else if("workminsAfter".equals(fieldName)){
            //计算扣减后应工作时长
            String workmins = Util.null2String(rs.getString("workmins"));	//应出勤分钟数
            if (!"".equals(workmins)){
              String workminsLeave = kqReportUtil.leaveTotal(flowData,id,"workminsAfter");	//扣减调休时长
              fieldValue = String.valueOf(Double.parseDouble(workmins)/60 - Double.parseDouble(workminsLeave));
            }else {
              fieldValue = "0.00";
            }
          }else if("attendanceMins".equals(fieldName)){
            //计算实际工作时长
            if (!"".equals(Util.null2String(rs.getString("attendanceMins")))){
              int attendanceMins = Integer.parseInt(Util.null2String(rs.getString("attendanceMins"))); //实际出勤分钟数
              fieldValue = kqReportUtil.attendanceMinsCal(attendanceMins,id,fromDate,toDate);
            }else {
              int attendanceMins = 0;
              fieldValue = kqReportUtil.attendanceMinsCal(attendanceMins,id,fromDate,toDate);
            }
          }else if("resignation".equals(fieldName)){
            //计算入离职缺勤
            fieldValue = kqReportUtil.resignationCal(id,fromDate,toDate);
          }else if("leaveTotal".equals(fieldName)){
            //计算事假总计时长
            fieldValue = kqReportUtil.leaveTotal(flowData,id,"leaveTotal");
          }else if ("businessWork".equals(fieldName)){
            //计算工作日出差时长
            fieldValue = kqReportUtil.evectionCol(id,2,fromDate,toDate);
          }else if ("businessWeekend".equals(fieldName)){
            //计算休息日出差时长
            fieldValue = kqReportUtil.evectionCol(id,3,fromDate,toDate);
          }else if ("businessHoliday".equals(fieldName)){
            //计算节假日出差时长
            fieldValue = kqReportUtil.evectionCol(id,1,fromDate,toDate);
          }else if ("workdaysReal".equals(fieldName)){
            //计算交补天数
            fieldValue = kqReportUtil.workDaysRealCal(id,fromDate,toDate);
          }else if ("lunchAllowance".equals(fieldName)){
            //计算午餐补助
            fieldValue = Util.null2String(rs.getString("lunchAllowance"));
          }else if ("dinnerAllowance".equals(fieldName)){
            //计算晚餐补助
            fieldValue = Util.null2String(rs.getString("dinnerAllowance"));
          }else if ("midnightAllowance".equals(fieldName)){
            //计算夜宵补助
            fieldValue = Util.null2String(rs.getString("midnightAllowance"));
          }else {
            fieldValue = Util.formatMultiLang(Util.null2String(rs.getString(fieldName)),""+user.getLanguage());
            fieldValue = getFieldValueByUnitType(fieldValue,kqReportFieldComInfo.getUnittype(fieldid));
          }
          fieldValue = Util.formatMultiLang(fieldValue,""+user.getLanguage());
          data.add(fieldValue);
        }
        if(showColumns.contains("kqCalendar")) {
          Map<String, Object> detialDatas = kqReportBiz.getDetialDatas(id, fromDate, toDate, user);
          isEnd = false;
          for (String date = fromDate; !isEnd; ) {
            if (date.equals(toDate)) isEnd = true;
            if (DateUtil.compDate(today, date) > 0) {
              data.add("");
            } else {
              if (detialDatas.get(id + "|" + date) != null) {
                data.add(((Map<String, Object>) detialDatas.get(id + "|" + date)).get("text"));
              } else {
                data.add(SystemEnv.getHtmlLabelName(26593, user.getLanguage()));
              }
            }
            cal.setTime(DateUtil.parseToDate(date));
            date = DateUtil.getDate(cal.getTime(), 1);
          }
        }
        dataList.add(data);
      }
      sheet.put("dataList", dataList);
      sheet.put("constraintList", constraintList);
      sheet.put("createFile", "1");
      lsSheet.add(sheet);

      workbook.put("sheet", lsSheet);
      String fileName = SystemEnv.getHtmlLabelName(390351, user.getLanguage())+" "+fromDate+" "+toDate;
      workbook.put("fileName", fileName);
      ExcelUtil ExcelUtil = new ExcelUtil();
      Map<String, Object> exportMap = ExcelUtil.export(workbook, request, response);
      retmap.putAll(exportMap);
      retmap.put("status", "1");
    } catch (Exception e) {
      retmap.put("status", "-1");
      retmap.put("message", SystemEnv.getHtmlLabelName(382661, user.getLanguage()));
      writeLog(e);
    }
    return retmap;
  }

  private Map<String, Object> getChildColumnsInfo(String parentid, User user) {
    Map<String, Object> returnMap = new HashMap<>();
    List<Object> titleList = new ArrayList<>();
    Map<String, Object> title = null;
    if (parentid.equals("attendanceSerial")) {//考勤班次
      KQShiftManagementComInfo kqShiftManagementComInfo = new KQShiftManagementComInfo();
      JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));
      List<String> serialIds = null;
      if (Util.null2String(jsonObj.get("attendanceSerial")).length() > 0) {
        serialIds = Util.splitString2List(Util.null2String(jsonObj.get("attendanceSerial")), ",");
      }
      for (int i = 0; serialIds != null && i < serialIds.size(); i++) {
        title = new HashMap<>();
        title.put("title", Util.formatMultiLang(kqShiftManagementComInfo.getSerial(serialIds.get(i)),""+user.getLanguage()));
        title.put("width", 30 * 256);
        titleList.add(title);
      }
    } else if (parentid.equals("leave")) {
      KQLeaveRulesBiz kqLeaveRulesBiz = new KQLeaveRulesBiz();
      List<Map<String, Object>> leaveRules = kqLeaveRulesBiz.getAllLeaveRules();
      for (int i = 0; leaveRules != null && i < leaveRules.size(); i++) {
        Map<String, Object> leaveRule = leaveRules.get(i);
        String name = Util.formatMultiLang(Util.null2String(leaveRule.get("name")),""+user.getLanguage());
        String unitType = Util.null2String(leaveRule.get("unitType"));
        String unitName = (KQUnitBiz.isLeaveHour(unitType)) ? SystemEnv.getHtmlLabelName(391, user.getLanguage()) : SystemEnv.getHtmlLabelName(1925, user.getLanguage());
        title = new HashMap<>();
        title.put("title", name + "(" + unitName + ")");
        title.put("width", 30 * 256);
        titleList.add(title);
      }
    }else if(parentid.equals("overtime")){
      String[] overtimeChild = {"overtime_nonleave","overtime_4leave","overtimeTotal"};
      for(int i=0;i<overtimeChild.length;i++){
        String id = overtimeChild[i];
        title = new HashMap();
        String fieldlabel = "";
        if("overtime_nonleave".equalsIgnoreCase(id)){
          fieldlabel = "125805";
          title.put("title", SystemEnv.getHtmlLabelNames(fieldlabel, user.getLanguage()));
          title.put("rowSpan","2");
        }else if("overtime_4leave".equalsIgnoreCase(id)){
          fieldlabel = "125804";
          title.put("title", SystemEnv.getHtmlLabelNames(fieldlabel, user.getLanguage()));
          title.put("rowSpan","2");
        }else{
          fieldlabel = "523";
          title.put("showDetial","1");
          String unitType = (KQOvertimeRulesBiz.getMinimumUnit()==3 || KQOvertimeRulesBiz.getMinimumUnit()==5 ||KQOvertimeRulesBiz.getMinimumUnit()==6)?"2":"1";
          String unitTypeName = "";
          if(Util.null2String(unitType).length()>0){
            if(unitType.equals("1")){
              unitTypeName=SystemEnv.getHtmlLabelName(1925, user.getLanguage());
            }else if(unitType.equals("2")){
              unitTypeName=SystemEnv.getHtmlLabelName(391, user.getLanguage());
            }else if(unitType.equals("3")){
              unitTypeName=SystemEnv.getHtmlLabelName(18083, user.getLanguage());
            }
          }
          title.put("title", SystemEnv.getHtmlLabelNames(fieldlabel, user.getLanguage())+ "(" + unitTypeName + ")");
        }

        Map<String,Object> mapChildColumnInfo = getChildColumnsInfo(id, user);
        int childWidth = 65;
        List<Object> childColumns = (List<Object>)mapChildColumnInfo.get("childColumns");
        if(childColumns.size()>0) {//跨列width取子列的width
          title.put("children", childColumns);
          childWidth = Util.getIntValue(Util.null2String(mapChildColumnInfo.get("sumChildColumnWidth")),65);
        }
        title.put("width", childWidth+"");
        titleList.add(title);
      }
    } else {
      KQReportFieldComInfo kqReportFieldComInfo = new KQReportFieldComInfo();
      while (kqReportFieldComInfo.next()) {
        if (kqReportFieldComInfo.getParentid().equals(parentid)) {
          if(!kqReportFieldComInfo.getReportType().equals("month"))continue;
          title = new HashMap<>();
          title.put("title", SystemEnv.getHtmlLabelNames(kqReportFieldComInfo.getFieldlabel(), user.getLanguage()) + "(" + KQReportBiz.getUnitType(kqReportFieldComInfo, user) + ")");
          title.put("width", 30 * 256);
          titleList.add(title);
        }
      }
    }
    returnMap.put("childColumns", titleList);
    return returnMap;
  }

  private List<Object>  getCascadeKeyColumnsInfo(String cascadeKey, User user){
    List<Object> titleList = new ArrayList<>();
    Map<String, Object> title = null;
    if(Util.null2String(cascadeKey).length()==0){
      return titleList;
    }
    List<String> lsCascadeKey = Util.splitString2List(cascadeKey,",");
    KQReportFieldComInfo kqReportFieldComInfo = new KQReportFieldComInfo();
    for(int i=0;i<lsCascadeKey.size();i++){
      kqReportFieldComInfo.setTofirstRow();
      while (kqReportFieldComInfo.next()) {
        if(!kqReportFieldComInfo.getReportType().equals("month"))continue;
        if (kqReportFieldComInfo.getFieldname().equals(lsCascadeKey.get(i))){
          title = new HashMap<>();
          title.put("title", SystemEnv.getHtmlLabelNames(kqReportFieldComInfo.getFieldlabel(), user.getLanguage()) + "(" + KQReportBiz.getUnitType(kqReportFieldComInfo, user) + ")");
          title.put("width", 30 * 256);
          titleList.add(title);
        }
      }
    }
    return titleList;
  }

  private String getFieldValueByUnitType(String fieldValue,String unittype){
    if (Util.null2String(unittype).length() > 0) {
      if (fieldValue.length() == 0) {
        fieldValue = "0";
      } else {
        if (unittype.equals("2")) {
          fieldValue = KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(fieldValue) / 60.0)));
        }
      }
    }
    return fieldValue;
  }
  @Override
  public BizLogContext getLogContext() {
    return null;
  }
}

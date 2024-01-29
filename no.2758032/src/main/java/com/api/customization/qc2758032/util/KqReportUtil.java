package com.api.customization.qc2758032.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.engine.kq.biz.*;
import com.engine.kq.util.KQDurationCalculatorUtil;
import com.engine.kq.util.PageUidFactory;
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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class KqReportUtil {
    Map<String, Object> params = null;
    User user = null;

    public KqReportUtil(Map<String, Object> params, User user){
        this.params = params;
        this.user = user;
    }


    /**
     * 获取报表数据
     * @return
     */
    public Map<String, Object> getKqReport(){
        Map<String,Object> retmap = new HashMap<String,Object>();
        //==zj 优化接口性能问题
        SimpleDateFormat fds = new SimpleDateFormat("yyyy-MM-dd");

        Calendar c = Calendar.getInstance();
        RecordSet rs = new RecordSet();
        String sql = "";

        try{
            String pageUid = PageUidFactory.getHrmPageUid("KQReport");

            SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
            DepartmentComInfo departmentComInfo = new DepartmentComInfo();
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            JobTitlesComInfo jobTitlesComInfo = new JobTitlesComInfo();
            KQLeaveRulesBiz kqLeaveRulesBiz = new KQLeaveRulesBiz();
            KQReportBiz kqReportBiz = new KQReportBiz();
            KqReportBizCustom kqReportBizCustom = new KqReportBizCustom();
            KqReportBizCustomUtil kqReportBizCustomUtil = new KqReportBizCustomUtil();

            JSONObject jsonObj = JSON.parseObject(Util.null2String(params.get("data")));


            String attendanceSerial = Util.null2String(jsonObj.get("attendanceSerial"));

            //获取时间范围
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

            //人员状态
            String status = Util.null2String(jsonObj.get("status"));    //人员状态
            String subCompanyId = Util.null2String(jsonObj.get("subCompanyId"));    //分部id
            String departmentId = Util.null2String(jsonObj.get("departmentId"));    //部门id
            String resourceId = Util.null2String(jsonObj.get("resourceId"));        //人员id
            String allLevel = Util.null2String(jsonObj.get("allLevel"));            //所有下属
            String isNoAccount = Util.null2String(jsonObj.get("isNoAccount"));
            String viewScope = Util.null2String(jsonObj.get("viewScope"));          //我的下属
            String departmentNumber = Util.null2String(jsonObj.get("departmentNumber"));          //部门编号

            String isFromMyAttendance = Util.null2String(jsonObj.get("isFromMyAttendance"));//是否是来自我的考勤的请求，如果是，不加载考勤报表权限共享的限制，不然我的考勤会提示无权限
            int pageIndex = Util.getIntValue(Util.null2String(jsonObj.get("pageIndex")), 1);
            int pageSize =  KQReportBiz.getPageSize(Util.null2String(jsonObj.get("pageSize")),pageUid,user.getUID());
            int count = 0;
            int pageCount = 0;
            int isHavePre = 0;
            int isHaveNext = 0;


            String rightSql = kqReportBizCustom.getReportRight("1",""+user.getUID(),"a");
            if(isFromMyAttendance.equals("1")){
                rightSql = "";
            }
            //==z 这里通过部门编号获取对应部门id
            if (departmentNumber.length() > 0){
                String departmentSelectSql = "select id from hrmdepartment where departmentcode in ("+departmentNumber+")";
                RecordSet rs1 = new RecordSet();
                rs1.executeQuery(departmentSelectSql);
                while (rs1.next()){
                    departmentId = Util.null2String(rs1.getString("id"))+",";
                }
                departmentId = departmentId.substring(0,departmentId.length() - 1);
            }

            List<Map<String, Object>> leaveRules = kqLeaveRulesBiz.getAllLeaveRules();
            List<Object> datas = new ArrayList();
            Map<String,Object> data = null;
            KQReportFieldComInfo kqReportFieldComInfo = new KQReportFieldComInfo();

            String forgotBeginWorkCheck_field = " sum(b.forgotBeginWorkCheck) ";
            boolean isEnd = false;
            String today = fds.format(new Date());
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
                    " a.certificatenum as certificatenum,b.groupid,sum(b.FORGOTCHECK) as forgotCheckEnd,sum(b.FORGOTBEGINWORKCHECK) as forgotCheckBegin,"+
                    " sum(b.workdays) as workdays,sum(b.workMins) as workMins,sum(b.attendancedays) as attendancedays," +
                    " sum(b.attendanceMins) as attendanceMins,sum(b.beLate) as beLate,sum(b.beLateMins) as beLateMins, " +
                    " sum(b.graveBeLate) as graveBeLate, sum(b.graveBeLateMins) as graveBeLateMins,sum(b.leaveEearly) as leaveEearly," +
                    " sum(b.leaveEarlyMins) as leaveEarlyMins, sum(b.graveLeaveEarly) as graveLeaveEarly, " +
                    " sum(b.graveLeaveEarlyMins) as graveLeaveEarlyMins,sum(b.absenteeism) as absenteeism, " +
                    " sum(b.signdays) as signdays,sum(b.signmins) as signmins, "+
                    " sum(b.absenteeismMins) as absenteeismMins, sum(b.forgotCheck)+"+forgotBeginWorkCheck_field+" as forgotCheck "+(definedFieldSum.length()>0?","+definedFieldSum+"":"");

            if(rs.getDBType().equals("oracle")){
                backFields = 	"/*+ index(kq_format_total IDX_KQ_FORMAT_TOTAL_KQDATE) */ "+backFields;
            }
            String sqlFrom = " from hrmresource a, kq_format_total b where a.id= b.resourceid and b.kqdate >='"+fromDate+"' and b.kqdate <='"+toDate+"'";
            String sqlWhere = rightSql;
            String groupBy = " group by b.groupid,a.id,a.lastname,a.workcode,a.dsporder,b.resourceid,a.subcompanyid1,a.departmentid,a.jobtitle,a.certificatenum ";
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

            if(status.length()>0){
                if (!status.equals("8") && !status.equals("9")) {
                    sqlWhere += " and a.status = "+status+ "";
                }else if (status.equals("8")) {
                    sqlWhere += " and (a.status = 0 or a.status = 1 or a.status = 2 or a.status = 3) ";
                }
            }

           /* sql = " select count(*) as c from ( select 1 as c "+sqlFrom+sqlWhere+groupBy+") t";
            rs.execute(sql);
            if (rs.next()){
                count = rs.getInt("c");
            }

            if (count <= 0) {
                pageCount = 0;
            }

            pageCount = count / pageSize + ((count % pageSize > 0) ? 1 : 0);

            isHaveNext = (pageIndex + 1 <= pageCount) ? 1 : 0;

            isHavePre = (pageIndex - 1 >= 1) ? 1 : 0;*/

            String orderBy = " order by t.dsporder asc, t.lastname asc ";
            String descOrderBy = " order by t.dsporder desc, t.lastname desc ";

            sql = backFields + sqlFrom  + sqlWhere + groupBy;


            pageIndex = 0;
            pageSize = 0;
            if (pageIndex > 0 && pageSize > 0) {
                if (rs.getDBType().equals("oracle")) {
                    sql = " select * from (select " + sql+") t "+orderBy;
                    sql = "select * from ( select row_.*, rownum rownum_ from ( " + sql + " ) row_ where rownum <= "
                            + (pageIndex * pageSize) + ") where rownum_ > " + ((pageIndex - 1) * pageSize);
                } else if (rs.getDBType().equals("mysql")) {
                    sql = " select * from (select " + sql+") t "+orderBy;
                    sql = "select t1.* from (" + sql + ") t1 limit " + ((pageIndex - 1) * pageSize) + "," + pageSize;
                }
                else if (rs.getDBType().equals("postgresql")) {
                    sql = " select * from (select " + sql+") t "+orderBy;
                    sql = "select t1.* from (" + sql + ") t1 limit " +pageSize + " offset " + ((pageIndex - 1) * pageSize);
                }
                else {
                    orderBy = " order by dsporder asc, lastname asc ";
                    descOrderBy = " order by dsporder desc, lastname desc ";
                    if (pageIndex > 1) {
                        int topSize = pageSize;
                        if (pageSize * pageIndex > count) {
                            topSize = count - (pageSize * (pageIndex - 1));
                        }
                        sql = " select top " + topSize + " * from ( select top  " + topSize + " * from ( select top "
                                + (pageIndex * pageSize) + sql + orderBy+ " ) tbltemp1 " + descOrderBy + ") tbltemp2 " + orderBy;
                    } else {
                        sql = " select top " + pageSize + sql+orderBy;
                    }
                }
            } else {
                sql = " select " + sql;
            }
            Map<String,Object> flowData = kqReportBizCustom.getFlowData(params,user);
            KqReportCustomUtil kqReportCustomUtil = new KqReportCustomUtil();
            RecordSet rsCustom = new RecordSet();
            new BaseBean().writeLog("(kq_format_total Sql)" + JSON.toJSONString(sql));
            rs.execute(sql);
            while (rs.next()) {
                data = new HashMap<>();
                kqReportFieldComInfo.setTofirstRow();
                String id = rs.getString("id");
                data.put("resourceId",id);
                while (kqReportFieldComInfo.next()){
                    if(!Util.null2String(kqReportFieldComInfo.getIsdataColumn()).equals("1"))continue;
                    if(!kqReportFieldComInfo.getReportType().equals("all") && !kqReportFieldComInfo.getReportType().equals("month"))continue;
                    if("leave".equalsIgnoreCase(kqReportFieldComInfo.getFieldname())&&leaveRules.size()==0){
                        continue;
                    }
                    String fieldName = kqReportFieldComInfo.getFieldname();
                    String fieldValue = "";
                    if(fieldName.equals("subcompany")){
                        String tmpSubcompanyId = Util.null2String(rs.getString("subcompanyid"));
                        if(tmpSubcompanyId.length()==0){
                            tmpSubcompanyId =  Util.null2String(resourceComInfo.getSubCompanyID(id));
                        }
                        data.put("subcompanyId",tmpSubcompanyId);
                        fieldValue = subCompanyComInfo.getSubCompanyname(tmpSubcompanyId);
                    }else if(fieldName.equals("department")){
                        String tmpDepartmentId = Util.null2String(rs.getString("departmentid"));
                        if(tmpDepartmentId.length()==0){
                            tmpDepartmentId =  Util.null2String(resourceComInfo.getDepartmentID(id));
                        }
                        data.put("departmentId",tmpDepartmentId);
                        fieldValue = departmentComInfo.getDepartmentname(tmpDepartmentId);
                    }else if(fieldName.equals("jobtitle")){
                        String tmpJobtitleId = Util.null2String(rs.getString("jobtitle"));
                        if(tmpJobtitleId.length()==0){
                            tmpJobtitleId =  Util.null2String(resourceComInfo.getJobTitle(id));
                        }
                        data.put("jobtitleId",tmpJobtitleId);
                        fieldValue = jobTitlesComInfo.getJobTitlesname(tmpJobtitleId);
                    }else if (fieldName.equals("allowanceDays")){
                        //==z 补助天数
                        fieldValue = kqReportCustomUtil.getAllowanceDays(id,flowData,rs.getString("attendancedays"));
                    }else if (fieldName.equals("attendanceCustomTotal")){
                        //==z 出勤总天数
                        fieldValue = kqReportCustomUtil.getAttendanceCustomTotal(id,flowData,rs.getString("attendancedays"));
                    }else if(fieldName.equals("attendanceSerial")){
                        List<String> serialIds = null;
                        if(attendanceSerial.length()>0){
                            serialIds = Util.splitString2List(attendanceSerial,",");
                        }
                        for(int i=0;serialIds!=null&&i<serialIds.size();i++){
                            data.put(serialIds.get(i), kqReportBizCustom.getSerialCount(id,fromDate,toDate,serialIds.get(i)));
                        }
                    }else if(kqReportFieldComInfo.getParentid().equals("overtime")||kqReportFieldComInfo.getParentid().equals("overtime_nonleave")
                            ||kqReportFieldComInfo.getParentid().equals("overtime_4leave")||fieldName.equals("businessLeave") || fieldName.equals("officialBusiness")){
                        if(fieldName.equals("overtimeTotal")){
                            String groupid = Util.null2String(rs.getString("groupid"));
                            new BaseBean().writeLog("flowData" + JSON.toJSONString(flowData));
                            new BaseBean().writeLog("(key)" + id+"|workingDayOvertime_4leave"+"|"+groupid);
                            double workingDayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|workingDayOvertime_4leave"+"|"+groupid)));
                            workingDayOvertime_4leave = workingDayOvertime_4leave<0?0:workingDayOvertime_4leave;
                            double restDayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|restDayOvertime_4leave"+"|"+groupid)));
                            restDayOvertime_4leave = restDayOvertime_4leave<0?0:restDayOvertime_4leave;
                            double holidayOvertime_4leave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|holidayOvertime_4leave"+"|"+groupid)));
                            holidayOvertime_4leave = holidayOvertime_4leave<0?0:holidayOvertime_4leave;

                            double workingDayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|workingDayOvertime_nonleave"+"|"+groupid)));
                            workingDayOvertime_nonleave = workingDayOvertime_nonleave<0?0:workingDayOvertime_nonleave;
                            double restDayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|restDayOvertime_nonleave"+"|"+groupid)));
                            restDayOvertime_nonleave = restDayOvertime_nonleave<0?0:restDayOvertime_nonleave;
                            double holidayOvertime_nonleave = Util.getDoubleValue(Util.null2String(flowData.get(id+"|holidayOvertime_nonleave"+"|"+groupid)));
                            holidayOvertime_nonleave = holidayOvertime_nonleave<0?0:holidayOvertime_nonleave;

                            fieldValue = KQDurationCalculatorUtil.getDurationRound(String.valueOf(workingDayOvertime_4leave+restDayOvertime_4leave+holidayOvertime_4leave+
                                    workingDayOvertime_nonleave+restDayOvertime_nonleave+holidayOvertime_nonleave));
                        }else if(fieldName.equals("businessLeave") || fieldName.equals("officialBusiness")){
                            String groupid = Util.null2String(rs.getString("groupid"));
                            String businessLeaveData = Util.null2s(Util.null2String(flowData.get(id+"|"+fieldName+"|"+groupid)),"0.0");
                            String backType = fieldName+"_back";
                            String businessLeavebackData = Util.null2s(Util.null2String(flowData.get(id+"|"+backType+"|"+groupid)),"0.0");
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
                        }else{
                            String groupid = Util.null2String(rs.getString("groupid"));
                            fieldValue = KQDurationCalculatorUtil.getDurationRound(Util.null2String(flowData.get(id+"|"+fieldName+"|"+groupid)));
                        }
                    } else {
                        fieldValue = Util.null2String(rs.getString(fieldName));
                        if(Util.null2String(kqReportFieldComInfo.getUnittype()).length()>0) {
                            if(fieldValue.length() == 0){
                                fieldValue="0";
                            }else{
                                if (kqReportFieldComInfo.getUnittype().equals("2")) {
                                    fieldValue = KQDurationCalculatorUtil.getDurationRound(("" + (Util.getDoubleValue(fieldValue) / 60.0)));
                                }
                            }
                        }
                    }
                    data.put(fieldName,fieldValue);
                }

                //请假
                List<Map<String, Object>> allLeaveRules = kqLeaveRulesBiz.getAllLeaveRules();
                Map<String, Object> leaveRule = null;
                for(int i=0;allLeaveRules!=null&&i<allLeaveRules.size();i++){
                    leaveRule = (Map<String, Object>)allLeaveRules.get(i);
                    String groupid = Util.null2String(rs.getString("groupid"));
                    String flowType = Util.null2String("leaveType_"+leaveRule.get("id"));
                    String leaveData = Util.null2String(flowData.get(id+"|"+flowType+"|"+groupid));
                    String flowLeaveBackType = Util.null2String("leavebackType_"+leaveRule.get("id"));
                    String leavebackData = Util.null2s(Util.null2String(flowData.get(id+"|"+flowLeaveBackType)),"0.0");
                    String b_flowLeaveData = "";
                    String flowLeaveData = "";
                    try{
                        //以防止出现精度问题
                        if(leaveData.length() == 0){
                            leaveData = "0.0";
                        }
                        if(leavebackData.length() == 0){
                            leavebackData = "0.0";
                        }
                        BigDecimal b_leaveData = new BigDecimal(leaveData);
                        BigDecimal b_leavebackData = new BigDecimal(leavebackData);
                        b_flowLeaveData = b_leaveData.subtract(b_leavebackData).toString();
                        if(Util.getDoubleValue(b_flowLeaveData, -1) < 0){
                            b_flowLeaveData = "0.0";
                        }
                    }catch (Exception e){
                        new BaseBean().writeLog("GetKQReportCmd:leaveData"+leaveData+":leavebackData:"+leavebackData+":"+e);
                    }

                    //考虑下冻结的数据
                    if(b_flowLeaveData.length() > 0){
                        flowLeaveData = KQDurationCalculatorUtil.getDurationRound(b_flowLeaveData);
                    }else{
                        flowLeaveData = KQDurationCalculatorUtil.getDurationRound(Util.null2String(Util.getDoubleValue(leaveData,0.0)-Util.getDoubleValue(leavebackData,0.0)));
                    }
                    data.put(flowType,flowLeaveData);
                }

                Map<String,Object> detialDatas = kqReportBizCustom.getDetialDatas(id,fromDate,toDate,user);
                /*Map<String,Object> detialDatas = kqReportBizCustomUtil.getDetialDatas(id,fromDate,toDate,user);*/
//        new KQLog().info("id:"+id+":detialDatas:"+detialDatas);
                isEnd = false;

                //把身份证,上下班缺勤次数带上,还有考勤月份和考勤组
                data.put("certificatenum",Util.null2s(rs.getString("certificatenum"),""));
                data.put("forgotCheckEnd",Util.null2s(rs.getString("forgotCheckEnd"),""));
                data.put("forgotCheckBegin",Util.null2s(rs.getString("forgotCheckBegin"),""));
              /*  String kqGroupId="";
                String customSql = " select DISTINCT b.GROUPNAME  from kq_format_total a,KQ_GROUP b where resourceid='"+id+"' and kqdate between '"+fromDate+"' and '"+toDate+"' and a.GROUPID = b.id";
                new BaseBean().writeLog("==zj==(考勤组获取)" + customSql);
                rsCustom.executeQuery(customSql);
                while (rsCustom.next()){
                    kqGroupId += Util.null2String(rsCustom.getString("groupname"))+",";
                }
                if (kqGroupId.length() > 0){
                    kqGroupId = kqGroupId.substring(0,kqGroupId.length()-1);
                }*/
                data.put("kqGroupId",Util.null2s(rs.getString("groupid"),""));
                String kqMonth = "";
                if (fromDate.length() > 7){
                     kqMonth = Util.null2String(fromDate.substring(0,7));
                }
                data.put("kqMonth",Util.null2s(kqMonth,""));


                for(String date=fromDate; !isEnd;) {
                    if(date.equals(toDate)) isEnd = true;
                    if(today.compareTo(date)<0){
                        data.put(date,"");
                    }else{
//            new KQLog().info("id:date:"+(id+"|"+date)+":detialDatas.get:"+detialDatas.get(id+"|"+date));
                        data.put(date,detialDatas.get(id+"|"+date)==null?SystemEnv.getHtmlLabelName(26593, user.getLanguage()):detialDatas.get(id+"|"+date));
                    }
                    //==zj 优化接口性能问题
                    c.setTime(fds.parse(date));
                    c.add(Calendar.DATE,1);
                    date = fds.format(c.getTime());
                }

                datas.add(data);
            }

            List<Object> lsHolidays = KQHolidaySetBiz.getHolidaySetListByScope(""+user.getUID(),fromDate,toDate);
            retmap.put("holidays", lsHolidays);

            retmap.put("datas",datas);
            retmap.put("pagesize", pageSize);
            retmap.put("pageindex", pageIndex);
            retmap.put("count", count);
            retmap.put("pagecount", pageCount);
            retmap.put("ishavepre", isHavePre);
            retmap.put("ishavenext", isHaveNext);

        }catch (Exception e){
            new BaseBean().writeLog("==zj==(获取报表数据错误)" + e);
        }

        return retmap;
    }


}

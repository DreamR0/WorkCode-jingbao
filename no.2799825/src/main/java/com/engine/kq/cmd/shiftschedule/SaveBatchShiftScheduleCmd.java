package com.engine.kq.cmd.shiftschedule;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.biz.SimpleBizLogger;
import com.engine.common.constant.BizLogSmallType4Hrm;
import com.engine.common.constant.BizLogType;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.*;
import com.engine.kq.cmd.shiftmanagement.toolkit.ShiftManagementToolKit;
import com.engine.kq.util.UtilKQ;
import weaver.common.DateUtil;
import weaver.conn.BatchRecordSet;
import weaver.conn.DBUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.SystemEnv;

import java.util.*;

public class SaveBatchShiftScheduleCmd extends AbstractCommonCommand<Map<String, Object>> {

  private SimpleBizLogger logger;

  public SaveBatchShiftScheduleCmd(Map<String, Object> params, User user) {
    this.user = user;
    this.params = params;
    this.logger = new SimpleBizLogger();

    String data = Util.null2String(params.get("data"));
    JSONObject jsonObj = JSON.parseObject(data);
    String groupId = Util.null2String(jsonObj.get("groupId"));//考勤组id
    BizLogContext bizLogContext = new BizLogContext();
    bizLogContext.setLogType(BizLogType.HRM_ENGINE);//模块类型
    bizLogContext.setBelongType(BizLogSmallType4Hrm.HRM_ENGINE_Schedule_Set);//所属大类型
    bizLogContext.setLogSmallType(BizLogSmallType4Hrm.HRM_ENGINE_Schedule_Set);//当前小类型
    bizLogContext.setParams(params);//当前request请求参数
    logger.setUser(user);//当前操作人
    String mainSql = "select * from kq_group where id = " + groupId + "  and (isdelete is null or isdelete <> '1') ";
    logger.setMainSql(mainSql, "id");//主表sql
    logger.setMainPrimarykey("id");//主日志表唯一key
    logger.setMainTargetNameColumn("groupname");
    SimpleBizLogger.SubLogInfo subLogInfo1 = logger.getNewSubLogInfo();
    String subSql1 =  "select * from kq_shiftschedule where groupid = " + groupId + "  and (isdelete is null or isdelete <> '1') ";
    subLogInfo1.setSubSql(subSql1,"id");
    logger.addSubLogInfo(subLogInfo1);
    logger.before(bizLogContext);
  }

  @Override
  public Map<String, Object> execute(CommandContext commandContext) {
    Map<String, Object> retmap = new HashMap<String, Object>();
    RecordSet rs = new RecordSet();
    BatchRecordSet bRs = new BatchRecordSet();
    String today = DateUtil.getCurrentDate();
    String sql = "";
    try{
      //必要的权限判断
      if(!HrmUserVarify.checkUserRight("HrmKQGroup:Add",user)) {
        retmap.put("status", "-1");
        retmap.put("message", SystemEnv.getHtmlLabelName(2012, user.getLanguage()));
        return retmap;
      }

      ResourceComInfo resourceComInfo = new ResourceComInfo();
      KQGroupComInfo kqGroupComInfo = new KQGroupComInfo();
      KQGroupBiz kqGroupBiz = new KQGroupBiz();
      String data = Util.null2String(params.get("data"));
      JSONObject jsonObj = JSON.parseObject(data);
      String groupId = Util.null2String(jsonObj.get("groupId"));//考勤组id
      String shiftScheduleType = Util.null2String(jsonObj.get("shiftScheduleType"));//排班类型
      String shiftScheduleDate = Util.null2String(jsonObj.get("shiftScheduleDate"));//排班日期
      String shiftScheduleBeginDate = Util.null2String(jsonObj.get("shiftScheduleBeginDate"));//排班开始日期
      String shiftScheduleEndDate = Util.null2String(jsonObj.get("shiftScheduleEndDate"));//排班结束日期
      String shiftScheduleMemberType = Util.null2String(jsonObj.get("shiftScheduleMemberType"));//排班人员类型
      String shiftScheduleMember = Util.null2String(jsonObj.get("shiftScheduleMember"));//排班人员
      String holidayType = Util.null2String(jsonObj.get("holidayType"));//遇双休日
	  String meetHolidays = Util.null2String(jsonObj.get("meetHolidays"));//遇节假日
	  String meetRestDays = Util.null2String(jsonObj.get("meetRestDays"));//遇调配休息日
      String serialId = Util.null2String(jsonObj.get("serialId"));//班次
      String shiftcycleId = Util.null2String(jsonObj.get("shiftcycleId"));//排班周期Id
      String isturning = Util.null2String(jsonObj.get("isturning"));//是否按人员轮巡排班

      List<List<Object>> paramInsert = new ArrayList<List<Object>>();
      List<List<Object>> paramUpdate = new ArrayList<List<Object>>();
      List<Object> params = null;
      String delIds = "";

      List<List<Object>> lsFormatParams = new ArrayList<>();
      List<Object> formatParams = new ArrayList<>();

      List<List<Object>> lsDeleteParams = new ArrayList<>();
      List<Object> deleteParams = null;

      List<List<Object>> lscheckSerialParams = new ArrayList<>();
      List<Object> checkSerialParams = new ArrayList<>();

	  List<List<Object>> lsDeleteCancelParams = new ArrayList<>();
	  List<Object> deleteCancelParams = null;

      List<String> lsGroupMembers = null;
      if(shiftScheduleMemberType.equals("1")){//所有考勤组成员
        lsGroupMembers = kqGroupBiz.getGroupMembers(groupId);
      }else{//考勤组成员
        lsGroupMembers = Util.splitString2List(shiftScheduleMember,",");
      }
	  List<String> excludeidList = new ArrayList<>();
		if(!Util.null2String(kqGroupComInfo.getExcludecount(groupId)).equals("1")) {
			String excludeid = Util.null2String(kqGroupComInfo.getExcludeid(groupId));
			if (excludeid.length() > 0) {
				excludeidList = Util.splitString2List(excludeid,",");
			}
		}
      Map<String,Object> mapShiftSchedule = new HashMap<>();
      sql = " select id, resourceid, kqdate from kq_shiftschedule where (isdelete is null or  isdelete <> '1') and groupid=? ";
      //有客户提下面的sql有性能问题，在表数据较多，并且该表数据改动较为频率不适合建索引，所以对mapShiftSchedule先缓存的数据范围过滤下，没必要全表缓存
      if("1".equals(shiftScheduleType)){//按天排班
        sql += " and kqdate='"+shiftScheduleDate+"' ";
      }else if("2".equals(shiftScheduleType)){
        sql += " and kqdate>='"+shiftScheduleBeginDate+"' and kqdate<='"+shiftScheduleEndDate+"' ";
      }
      rs.executeQuery(sql,groupId);
      while(rs.next()){
        mapShiftSchedule.put(rs.getString("kqdate")+"|"+rs.getString("resourceid"),rs.getString("id"));
      }
      String id = "";
      if(shiftScheduleType.equals("1")){//按天排班
        for(String resourceid:lsGroupMembers){
			// 排除无需考勤人员
			if(excludeidList != null && excludeidList.contains(resourceid)) {
				continue;
			}
          id =  Util.null2String(mapShiftSchedule.get(shiftScheduleDate+"|"+resourceid));
          //if(weaver.common.DateUtil.timeInterval(shiftScheduleDate,today)>0)continue;//今天之前的无需处理
          if(id.length()>0){
            if(serialId.length()==0){
              if(delIds.length()>0)delIds+=",";
              delIds+=id;
            }else {
              params = new ArrayList<Object>();
              params.add(serialId);
              params.add(id);
              paramUpdate.add(params);
            }
          }else{
            if(serialId.length()==0)continue;;
            params = new ArrayList<Object>();
            params.add(shiftScheduleDate);
            params.add(serialId);
            params.add(resourceid);
            params.add(groupId);
            paramInsert.add(params);
          }

          if(DateUtil.timeInterval(shiftScheduleDate,today)>=0) {
            formatParams = new ArrayList<>();
            formatParams.add(resourceid);
            formatParams.add(shiftScheduleDate);
            lsFormatParams.add(formatParams);
          }

          deleteParams = new ArrayList<>();
          deleteParams.add(resourceid);
          deleteParams.add(shiftScheduleDate);
          lsDeleteParams.add(deleteParams);

          checkSerialParams = new ArrayList<>();
          checkSerialParams.add(resourceid);
          checkSerialParams.add(shiftScheduleDate);
          checkSerialParams.add(serialId);
          lscheckSerialParams.add(checkSerialParams);
        }
      }else if(shiftScheduleType.equals("2")){//按周期排班
        List<String> lsDate = new ArrayList<>();
        Calendar cal = DateUtil.getCalendar();
        writeLog("shiftScheduleBeginDate==" + shiftScheduleBeginDate + "shiftScheduleEndDate==" + shiftScheduleEndDate);
        if (DateUtil.timeInterval(shiftScheduleEndDate, shiftScheduleBeginDate) > 0)
        {
          retmap.put("status", "-1");
          retmap.put("message", ""+ SystemEnv.getHtmlLabelName(10005344,weaver.general.ThreadVarLanguage.getLang())+"");
          return retmap;
        }

        int shiftcycleday = 0;
        String[] shiftcycleserialids = null;
        sql = "select shiftcycleday,shiftcycleserialids from kq_group_shiftcycle where id = ? order by id asc ";
        rs.executeQuery(sql,shiftcycleId);
        if(rs.next()){
          shiftcycleday = rs.getInt("shiftcycleday");
          shiftcycleserialids = Util.splitString(Util.null2String(rs.getString("shiftcycleserialids")), ",");
        }

        boolean isEnd = false;
        for(String date=shiftScheduleBeginDate; !isEnd;) {
          if(date.equals(shiftScheduleEndDate)) isEnd = true;
          lsDate.add(date);
          cal.setTime(DateUtil.parseToDate(date));
          date = DateUtil.getDate(cal.getTime(), 1);
        }


        new BaseBean().writeLog("lsDate:" + Util.null2String(lsDate));
        for(String resourceid:lsGroupMembers){
			// 排除无需考勤人员
			if(excludeidList != null && excludeidList.contains(resourceid)) {
				continue;
			}
          boolean needShiftSchedule = false;
          int dayCount = 0;
          int resourceOrder = lsGroupMembers.indexOf(resourceid);
          for(int i=0;i<lsDate.size();i++) {
            boolean isNon = false;
            //if(weaver.common.DateUtil.timeInterval(date,today)>0)continue;//今天之前的无需处理
            //按照人员顺序进行排班
            if (i  < resourceOrder && "1".equals(isturning)){
              isNon = true;
            }
            new BaseBean().writeLog("人员排班情况：" + resourceid +" | "+lsDate.get(i) +" | " + isNon );
            String date = lsDate.get(i);
            id =  Util.null2String(mapShiftSchedule.get(date+"|"+resourceid));
            needShiftSchedule = true;
            int changeType= KQHolidaySetBiz.getChangeType(groupId, date);
            if (changeType != 1 && changeType != 3 && !holidayType.equals("1")) {//遇双休日继续排班
              int weekDay = DateUtil.getWeek(date);
              if(weekDay>5){
                needShiftSchedule = false;
              }
            }
			if(!meetHolidays.equals("1")){//遇节假日继续排班
				if(changeType==1){
					needShiftSchedule = false;
				}
			}
			if(!meetRestDays.equals("1")){//遇调配休息日继续排班
				if(changeType==3){
					needShiftSchedule = false;
				}
			}

            if(needShiftSchedule){
              //如果不在轮询周期，按照人员顺序往后一天排班
              if (!isNon){
                dayCount++;
              }
              int idx = dayCount % shiftcycleday;
              if (idx == 0) {//周期最后一天
                serialId = Util.null2String(shiftcycleserialids[shiftcycleserialids.length - 1]);
              }else if (isNon){
                serialId = "-1";
              }else {
                serialId = Util.null2String(shiftcycleserialids[idx-1]);
              }
              if(id.length()>0){
                if(serialId.length()==0){
                  if(delIds.length()>0)delIds+=",";
                  delIds+=id;
                }else {
                  params = new ArrayList<Object>();
                  params.add(serialId);
                  params.add(id);
                  paramUpdate.add(params);
                }
              }else{
                if(serialId.length()>0){
                  params = new ArrayList<Object>();
                  params.add(date);
                  params.add(serialId);
                  params.add(resourceid);
                  params.add(groupId);
                  paramInsert.add(params);
                }
              }
            }else{
				deleteCancelParams = new ArrayList<>();
				deleteCancelParams.add(resourceid);
				deleteCancelParams.add(date);
				lsDeleteCancelParams.add(deleteCancelParams);
            }
            if(DateUtil.timeInterval(date,today)>=0) {
              formatParams = new ArrayList<>();
              formatParams.add(resourceid);
              formatParams.add(date);
              lsFormatParams.add(formatParams);
            }

            deleteParams = new ArrayList<>();
            deleteParams.add(resourceid);
            deleteParams.add(date);
            lsDeleteParams.add(deleteParams);

            checkSerialParams = new ArrayList<>();
            checkSerialParams.add(resourceid);
            checkSerialParams.add(shiftScheduleDate);
            checkSerialParams.add(serialId);
            lscheckSerialParams.add(checkSerialParams);
          }
        }
      }

      sql = " update kq_shiftschedule set serialid=?,isDelete=0 where id = ? ";
      for (int i = 0; paramUpdate != null && i < paramUpdate.size(); i++) {
        List<Object> update_params = paramUpdate.get(i);
        String serialid = Util.null2String(update_params.get(0));
        String tmp_id = Util.null2String(update_params.get(1));
        rs.executeUpdate(sql, serialid,tmp_id);
      }

      sql = "insert into kq_shiftschedule (kqdate,serialid,resourceid,groupid,isDelete) values(?,?,?,?,0)";
      for (int i = 0; paramInsert != null && i < paramInsert.size(); i++) {
        List<Object> insert_params = paramInsert.get(i);
        String kqdate = Util.null2String(insert_params.get(0));
        String serialid = Util.null2String(insert_params.get(1));
        String resourceid = Util.null2String(insert_params.get(2));
        String groupid = Util.null2String(insert_params.get(3));
        rs.executeUpdate(sql, kqdate,serialid,resourceid,groupid);
      }

      if(delIds.length()>0){
        List sqlParams = new ArrayList();
        Object[] objects= DBUtil.transListIn(delIds,sqlParams);
        sql = "update kq_shiftschedule set isdelete =1 where id in("+objects[0]+") ";
        rs.executeUpdate(sql, sqlParams);
      }
			//删除批量排班，遇双休日、节假日、调配工作日的取消排班
	  sql = " update kq_shiftschedule set isdelete =1 where resourceid = ? and kqdate = ? ";
	  for (int i = 0; lsDeleteCancelParams != null && i < lsDeleteCancelParams.size(); i++) {
        List<Object> delete_params = lsDeleteCancelParams.get(i);
        String resourceid = Util.null2String(delete_params.get(0));
        String kqdate = Util.null2String(delete_params.get(1));
        rs.executeUpdate(sql, resourceid,kqdate);
      }
	  
      //删除之前的排的班次，一天只能有一个班次
      sql = " update kq_shiftschedule set isdelete =1 where resourceid = ? and kqdate = ? and groupid != "+groupId;
      for (int i = 0; lsDeleteParams != null && i < lsDeleteParams.size(); i++) {
        List<Object> delete_params = lsDeleteParams.get(i);
        String resourceid = Util.null2String(delete_params.get(0));
        String kqdate = Util.null2String(delete_params.get(1));
        rs.executeUpdate(sql, resourceid,kqdate);
      }

      sql = " update kq_shiftschedule set isdelete =1 where resourceid = ? and kqdate = ? and serialid != ? ";
      for (int i = 0; lscheckSerialParams != null && i < lscheckSerialParams.size(); i++) {
        List<Object> check_params = lscheckSerialParams.get(i);
        String resourceid = Util.null2String(check_params.get(0));
        String kqdate = Util.null2String(check_params.get(1));
        String serialid = Util.null2String(check_params.get(2));
        rs.executeUpdate(sql, resourceid,kqdate,serialid);
      }

      new KQShiftScheduleComInfo().removeCache();

      //刷新报表数据
      new KQFormatBiz().format(lsFormatParams);

      retmap.put("status", "1");
      retmap.put("message", SystemEnv.getHtmlLabelName(18758, user.getLanguage()));
    }catch (Exception e) {
      retmap.put("status", "-1");
      retmap.put("message",  SystemEnv.getHtmlLabelName(382661,user.getLanguage()));
      writeLog(e);
    }
    return retmap;
  }

  @Override
  public BizLogContext getLogContext() {
    return null;
  }
  @Override
  public List<BizLogContext> getLogContexts() {
    return logger.getBizLogContexts();
  }
}

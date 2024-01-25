package com.engine.kq.cmd.shiftschedule;

import com.api.browser.bean.SearchConditionItem;
import com.api.browser.bean.SearchConditionOption;
import com.api.browser.util.ConditionFactory;
import com.api.browser.util.ConditionType;
import com.api.hrm.bean.HrmFieldBean;
import com.api.hrm.bean.SelectOption;
import com.api.hrm.bean.WeaRadioGroup;
import com.api.hrm.util.HrmFieldSearchConditionComInfo;
import com.cloudstore.dev.api.util.Util_TableMap;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQGroupBiz;
import com.engine.kq.biz.KQGroupComInfo;
import com.engine.kq.biz.KQHolidaySetBiz;
import com.engine.kq.biz.KQShiftManagementComInfo;
import com.engine.kq.util.PageUidFactory;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.filter.XssUtil;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.SystemEnv;

import java.util.*;

public class GetBatchShiftScheduleFromCmd extends AbstractCommonCommand<Map<String, Object>> {

	public GetBatchShiftScheduleFromCmd(Map<String, Object> params, User user) {
		this.user = user;
		this.params = params;
	}

	@Override
	public Map<String, Object> execute(CommandContext commandContext) {
		Map<String, Object> retmap = new HashMap<String, Object>();
		RecordSet rs = new RecordSet();
		String sql = "";
		List<HrmFieldBean> lsField = new ArrayList<HrmFieldBean>();
		HrmFieldBean hrmFieldBean = null;
		List<SearchConditionOption> options = null;
		Map<String,SearchConditionItem> mapItem = new HashMap<>();
		try{
			//必要的权限判断
			if(!HrmUserVarify.checkUserRight("HrmKQGroup:Add",user)) {
		  	retmap.put("status", "-1");
		  	retmap.put("message", SystemEnv.getHtmlLabelName(2012, user.getLanguage()));
		  	return retmap;
		  }

			KQGroupComInfo kQGroupComInfo = new KQGroupComInfo();
			KQShiftManagementComInfo kQShiftManagementComInfo = new KQShiftManagementComInfo();
			String groupId = Util.null2String(params.get("groupId"));//考勤组id

		  //排班类型、排班日期、排班人员、班次
			//排班类型、排班开始日期、排班人员、遇节假日、排班周期
			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftScheduleType");
			hrmFieldBean.setFieldlabel("389494");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			hrmFieldBean.setFieldvalue("1");
			options = new ArrayList<SearchConditionOption>();
			options.add(new SearchConditionOption("1",SystemEnv.getHtmlLabelName(389506, user.getLanguage()),true));
			options.add(new SearchConditionOption("2",SystemEnv.getHtmlLabelName(389507, user.getLanguage())));

			hrmFieldBean.setSelectOption(options);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftScheduleDate");
			hrmFieldBean.setFieldlabel("16694");
			hrmFieldBean.setFieldhtmltype("3");
			hrmFieldBean.setType("2");
			hrmFieldBean.setIsFormField(true);
			hrmFieldBean.setViewAttr(3);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftScheduleBeginDate");
			hrmFieldBean.setFieldlabel("16694");
			hrmFieldBean.setFieldhtmltype("3");
			hrmFieldBean.setType("2");
			hrmFieldBean.setViewAttr(3);
			hrmFieldBean.setIsFormField(true);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftScheduleEndDate");
			hrmFieldBean.setFieldlabel("16694");
			hrmFieldBean.setFieldhtmltype("3");
			hrmFieldBean.setType("2");
			hrmFieldBean.setViewAttr(3);
			hrmFieldBean.setIsFormField(true);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftScheduleMember");
			hrmFieldBean.setFieldlabel("125839");
			hrmFieldBean.setFieldhtmltype("3");
			hrmFieldBean.setType("17");
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("holidayType");
			hrmFieldBean.setFieldlabel("500436");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			options = new ArrayList<SearchConditionOption>();
			options.add(new SearchConditionOption("1",SystemEnv.getHtmlLabelName(125899, user.getLanguage()),true));
			options.add(new SearchConditionOption("2",SystemEnv.getHtmlLabelName(125837, user.getLanguage())));
			hrmFieldBean.setSelectOption(options);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("meetHolidays");
			hrmFieldBean.setFieldlabel("529182");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			options = new ArrayList<SearchConditionOption>();
			options.add(new SearchConditionOption("1",SystemEnv.getHtmlLabelName(125899, user.getLanguage()),true));
			options.add(new SearchConditionOption("2",SystemEnv.getHtmlLabelName(125837, user.getLanguage())));
			hrmFieldBean.setSelectOption(options);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("meetRestDays");
			hrmFieldBean.setFieldlabel("528922");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			options = new ArrayList<SearchConditionOption>();
			options.add(new SearchConditionOption("1",SystemEnv.getHtmlLabelName(125899, user.getLanguage()),true));
			options.add(new SearchConditionOption("2",SystemEnv.getHtmlLabelName(125837, user.getLanguage())));
			hrmFieldBean.setSelectOption(options);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("serialId");
			hrmFieldBean.setFieldlabel("24803");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			options = new ArrayList<SearchConditionOption>();
			String[] serialids = Util.splitString(Util.null2String(kQGroupComInfo.getSerialids(groupId)),",");
			for(int i=0;serialids!=null&&i<serialids.length;i++){
				options.add(new SearchConditionOption(serialids[i],kQShiftManagementComInfo.getSerial(serialids[i]),i==0));
			}
			options.add(new SearchConditionOption("-1", SystemEnv.getHtmlLabelName(26593, user.getLanguage())));
			hrmFieldBean.setSelectOption(options);
			lsField.add(hrmFieldBean);

			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("shiftcycleId");
			hrmFieldBean.setFieldlabel("389103");
			hrmFieldBean.setFieldhtmltype("5");
			hrmFieldBean.setType("1");
			options = new ArrayList<SearchConditionOption>();
			sql = "select id,shiftcyclename,shiftcycleserialids from kq_group_shiftcycle where groupid = ? order by id asc ";
			rs.executeQuery(sql,groupId);
			while(rs.next()){
				options.add(new SearchConditionOption(Util.null2String(rs.getString("id")),Util.null2String(rs.getString("shiftcyclename")),true));
				hrmFieldBean.setSelectOption(options);
			}
			lsField.add(hrmFieldBean);




			HrmFieldSearchConditionComInfo hrmFieldSearchConditionComInfo = new HrmFieldSearchConditionComInfo();
			SearchConditionItem searchConditionItem = null;

			//=z 增加是否"按人员轮巡排班"
			hrmFieldBean = new HrmFieldBean();
			hrmFieldBean.setFieldname("isturning");
			hrmFieldBean.setFieldlabel("-2024001");
			hrmFieldBean.setFieldhtmltype("4");
			hrmFieldBean.setType("1");
			hrmFieldBean.setFieldvalue("0");
			hrmFieldBean.setIsFormField(true);
			hrmFieldBean.setViewAttr(2);
			lsField.add(hrmFieldBean);

			for (int i = 0; i < lsField.size(); i++) {
				searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(lsField.get(i), user);
				if(lsField.get(i).getFieldname().equals("shiftScheduleMember")){
					XssUtil xssUtil = new XssUtil();
					Map<String,Object> tmpParams = new HashMap<>();
					tmpParams.put("groupId",groupId);
					tmpParams.put("isNoAccount",1);
					String groupMemberSql = new KQGroupBiz().getGroupMemberSql(tmpParams);
					String inSqlWhere = " hr.id in (select resourceid from (" + groupMemberSql + ") t1)";
					String inSqlWhere1 = " t1.id in (select resourceid from (" + groupMemberSql + ") t1)";
					if(!Util.null2String(kQGroupComInfo.getExcludecount(groupId)).equals("1")) {
						//剔除无需排班人员
						String excludeid = Util.null2String(kQGroupComInfo.getExcludeid(groupId));
						if (excludeid.length() > 0) {
							inSqlWhere += " and hr.id not in (" + excludeid + ")";
							inSqlWhere1 += " and t1.id not in (" + excludeid + ")";
						}
					}
					searchConditionItem.getBrowserConditionParam().getDataParams().put("sqlwhere", xssUtil.put(inSqlWhere));
					searchConditionItem.getBrowserConditionParam().getCompleteParams().put("sqlwhere", xssUtil.put(inSqlWhere1));
				}
				mapItem.put(lsField.get(i).getFieldname(),searchConditionItem);
			}

			//下拉
			List<Object> condition  = new ArrayList<Object>();
			condition.add(mapItem.get("shiftScheduleType"));

			//按天排班
			Map<String,Object> conditionMap = new HashMap<String, Object>();
			List<Object> conditionlist  = null;
			List<Object> conditionlist2  = null;

			conditionlist  = new ArrayList<Object>();
			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("shiftScheduleDate"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			WeaRadioGroup wrg = new WeaRadioGroup();
			wrg.setLabel(SystemEnv.getHtmlLabelName(125839, user.getLanguage()));
			List<Object> option = new ArrayList<>();
			Map<String, Object> selectLinkageDatas = new HashMap<String, Object>();
			selectLinkageDatas.put("2" ,mapItem.get("shiftScheduleMember"));
			wrg.setSelectLinkageDatas(selectLinkageDatas);
			option.add(new SelectOption("1",SystemEnv.getHtmlLabelName(389496, user.getLanguage()),true));
			option.add(new SelectOption("2",SystemEnv.getHtmlLabelName(33210, user.getLanguage())));

			List<String>  domkey =  new  ArrayList<String>();
			wrg.setOptions(option);
			wrg.setConditionType("SELECT_LINKAGE");
			wrg.setFieldcol(18);
			wrg.setLabelcol(6);
			domkey.add("shiftScheduleMemberType");
			wrg.setDomkey(domkey);
			conditionlist2.add(wrg);
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("serialId"));
			conditionlist.add(conditionlist2);
			conditionMap.put("1",conditionlist);

			//按周期排班
			conditionlist  = new ArrayList<Object>();
			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("shiftScheduleBeginDate"));
			conditionlist2.add(mapItem.get("shiftScheduleEndDate"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(wrg);
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("holidayType"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("meetHolidays"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("meetRestDays"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("shiftcycleId"));
			conditionlist.add(conditionlist2);

			conditionlist2  = new ArrayList<Object>();
			conditionlist2.add(mapItem.get("isturning"));
			conditionlist.add(conditionlist2);

			conditionMap.put("2",conditionlist);
			condition.add(conditionMap);

			retmap.put("conditions", condition);
			retmap.put("status", "1");
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

}

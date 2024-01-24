package com.engine.kq.wfset.bean;

import com.engine.kq.enums.DurationTypeEnum;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * 请假，出差，公出，加班，异常bean
 */
public class SplitBean {

  private String requestId;

  /**
   * 2个用处
   * 1、销假流程和考勤变更流程生成请假数据的时候记录下原流程的requestid
   * 2、请假流程和考勤变更流程被销假流程抵消掉后记录下是对应销假流程的requestid
   */
  private String leavebackrequestid;

  private String workflowId;

  private String usedetail;

  /**主表id
   *
   */
  private String dataId;

  /**
   * 明细表id
   */
  private String detailId;

  private String resourceId;

  private String fromDate;

  private String fromTime;

  private String toDate;

  private String toTime;

  private String newLeaveType;

  private String duration;
  /**
   * 对加班而言 节假日加班时长
   */
  private String pub_duration;
  /**
   * 对加班而言 工作日加班时长
   */
  private String work_duration;
  /**
   * 对加班而言 休息日加班时长
   */
  private String rest_duration;

  /**
   * 实际的表单名称
   */
  private String tablenamedb;

  /**
   * 表单上数据库里存的开始日期字段
   */
  private String fromdatedb;

  /**
   * 表单上数据库里存的开始时间字段
   */
  private String fromtimedb;

  /**
   * 表单上数据库里存的结束日期字段
   */
  private String todatedb;

  /**
   * 表单上数据库里存的结束时间字段
   */
  private String totimedb;

  /**
   * 表单上数据库里存的时长字段
   */
  private String durationDB;

  /**
   * 时长计算规则
   * 1-按天、2-按半天、3-按小时、4-按整天
   */
  private String durationrule;
  /**
   * 时长计算方式
   * 1-按照工作日计算请假时长
   * 2-按照自然日计算请假时长
   */
  private String computingMode;

  private String vacationInfo;

  /**
   * 作为请假冻结表里，1表示冻结，2表示释放,3表示作废的标识位
   *
   * 作为考勤拆分表里的有效标识默认为0，如果请假流程被销假消掉了。那么就变为1了
   * 作为考勤变更流程里的有效表示，默认为0，如果是被考勤变更或者撤销了，那么变为1
   */
  private String status;

  /**
   * 出差里用到的同行人
   */
  private String companion;

  /**
   * 拆分后的流程的所属日期，因为跨天情况下半天规则或者整天规则再时间赋值的时候可能会一天的流程拆分成两天
   */
  private String belongDate;

  /**
   * 得到实际时长分钟数
   */
  private double D_Mins;
  /**
   * 当前日期的班次
   */
  private String serialid;

  /**
   * 加班用的 当前日期是何种类型
   * 1-公众假日、2-工作日、3-休息日
   * 考勤变更流程用的
   * 1表示出差，2表示公出
   */
  private int changeType;

  /**
   * 加班用的前一天是何种类型
   * 1-公众假日、2-工作日、3-休息日
   */
  private int preChangeType;

  /**
   * 人员对应的分部id
   */
  private String subcompanyid;

  /**
   * 人员对应的部门id
   */
  private String departmentid;

  /**
   * 人员对应的岗位id
   */
  private String jobtitle;

  private DurationTypeEnum durationTypeEnum;

  /**
   * 工作时长
   */
  private int workmins;

  /**
   * 按照自然日计算的时候一天对应的时长
   */
  private double oneDayHour;

  /**
   * 人员对应的考勤组id
   */
  private String groupid;

  /**
   * 半天，整天单位的时候，判断是值显示还是下拉框显示
   * 1下拉框，2是值，默认是1
   */
  private String timeselection;

  private ProcessChangeSplitBean processChangeSplitBean;

  /**
   * 是否是同行人 1表示是
   */
  private String iscompanion;
  /**
   * 请假类型按照自然日计算的时候，是否排除休息日或者节假日
   * 0-不排除
   * 1-排除节假日
   * 2-排除休息日
   * 1,2-排除节假日和休息日
   */
  private String filterholidays;

  /**
   * 加班补偿方式，是下拉框，0是生成调休，非0是不生成调休
   */
  private String overtime_type;

  /**
   * 折算方式 1是四舍五入 2是向上取整 3是向下取整
   */
  private String conversion;

  /**
   * 用来判断调休假扣除哪个月份调休余额
   */
  private String reduceMonth;

  private List<OvertimeBalanceTimeBean> overtimeBalanceTimeBeans;

  public SplitBean() {
    this.requestId = "";
    this.leavebackrequestid = "";
    this.workflowId = "";
    this.usedetail = "";
    this.dataId = "";
    this.detailId = "";
    this.resourceId = "";
    this.fromDate = "";
    this.fromTime = "";
    this.toDate = "";
    this.toTime = "";
    this.newLeaveType = "";
    this.duration = "";
    this.tablenamedb = "";
    this.fromdatedb = "";
    this.fromtimedb = "";
    this.todatedb = "";
    this.totimedb = "";
    this.durationDB = "";
    this.durationrule = "";
    this.computingMode = "";
    this.vacationInfo = "";
    this.status = "";
    this.companion = "";
    this.belongDate = "";
    D_Mins = 0;
    this.serialid = "";
    this.changeType = 0;
    this.preChangeType = 0;
    this.subcompanyid = "";
    this.departmentid = "";
    this.jobtitle = "";
    this.workmins = 0;
    this.oneDayHour = 0.0;
    this.groupid = "";
    this.pub_duration = "";
    this.work_duration = "";
    this.rest_duration = "";
    this.timeselection = "1";
    processChangeSplitBean = new ProcessChangeSplitBean();
    this.iscompanion = "";
    this.filterholidays = "";
    this.overtimeBalanceTimeBeans = Lists.newArrayList();
    this.overtime_type = "";
    this.conversion = "";
    this.reduceMonth = "";
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getUsedetail() {
    return usedetail;
  }

  public void setUsedetail(String usedetail) {
    this.usedetail = usedetail;
  }

  public String getDataId() {
    return dataId;
  }

  public void setDataId(String dataId) {
    this.dataId = dataId;
  }

  public String getDetailId() {
    return detailId;
  }

  public void setDetailId(String detailId) {
    this.detailId = detailId;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getFromDate() {
    return fromDate;
  }

  public void setFromDate(String fromDate) {
    this.fromDate = fromDate;
  }

  public String getFromTime() {
    return fromTime;
  }

  public void setFromTime(String fromTime) {
    this.fromTime = fromTime;
  }

  public String getToDate() {
    return toDate;
  }

  public void setToDate(String toDate) {
    this.toDate = toDate;
  }

  public String getToTime() {
    return toTime;
  }

  public void setToTime(String toTime) {
    this.toTime = toTime;
  }

  public String getNewLeaveType() {
    return newLeaveType;
  }

  public void setNewLeaveType(String newLeaveType) {
    this.newLeaveType = newLeaveType;
  }

  public String getVacationInfo() {
    return vacationInfo;
  }

  public void setVacationInfo(String vacationInfo) {
    this.vacationInfo = vacationInfo;
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public String getDurationDB() {
    return durationDB;
  }

  public void setDurationDB(String durationDB) {
    this.durationDB = durationDB;
  }

  public String getDurationrule() {
    return durationrule;
  }

  public void setDurationrule(String durationrule) {
    this.durationrule = durationrule;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCompanion() {
    return companion;
  }

  public void setCompanion(String companion) {
    this.companion = companion;
  }

  public String getBelongDate() {
    return belongDate;
  }

  public void setBelongDate(String belongDate) {
    this.belongDate = belongDate;
  }

  public String getComputingMode() {
    return computingMode;
  }

  public void setComputingMode(String computingMode) {
    this.computingMode = computingMode;
  }

  public DurationTypeEnum getDurationTypeEnum() {
    return durationTypeEnum;
  }

  public void setDurationTypeEnum(DurationTypeEnum durationTypeEnum) {
    this.durationTypeEnum = durationTypeEnum;
  }

  public double getD_Mins() {
    return D_Mins;
  }

  public void setD_Mins(double d_Mins) {
    D_Mins = d_Mins;
  }

  public String getSerialid() {
    return serialid;
  }

  public void setSerialid(String serialid) {
    this.serialid = serialid;
  }

  public int getChangeType() {
    return changeType;
  }

  public void setChangeType(int changeType) {
    this.changeType = changeType;
  }

  public String getSubcompanyid() {
    return subcompanyid;
  }

  public void setSubcompanyid(String subcompanyid) {
    this.subcompanyid = subcompanyid;
  }

  public String getDepartmentid() {
    return departmentid;
  }

  public void setDepartmentid(String departmentid) {
    this.departmentid = departmentid;
  }

  public String getJobtitle() {
    return jobtitle;
  }

  public void setJobtitle(String jobtitle) {
    this.jobtitle = jobtitle;
  }

  public int getWorkmins() {
    return workmins;
  }

  public void setWorkmins(int workmins) {
    this.workmins = workmins;
  }

  public String getTablenamedb() {
    return tablenamedb;
  }

  public void setTablenamedb(String tablenamedb) {
    this.tablenamedb = tablenamedb;
  }

  public String getFromdatedb() {
    return fromdatedb;
  }

  public void setFromdatedb(String fromdatedb) {
    this.fromdatedb = fromdatedb;
  }

  public String getFromtimedb() {
    return fromtimedb;
  }

  public void setFromtimedb(String fromtimedb) {
    this.fromtimedb = fromtimedb;
  }

  public String getTodatedb() {
    return todatedb;
  }

  public void setTodatedb(String todatedb) {
    this.todatedb = todatedb;
  }

  public String getTotimedb() {
    return totimedb;
  }

  public void setTotimedb(String totimedb) {
    this.totimedb = totimedb;
  }

  public String getLeavebackrequestid() {
    return leavebackrequestid;
  }

  public void setLeavebackrequestid(String leavebackrequestid) {
    this.leavebackrequestid = leavebackrequestid;
  }

  public double getOneDayHour() {
    return oneDayHour;
  }

  public void setOneDayHour(double oneDayHour) {
    this.oneDayHour = oneDayHour;
  }

  public int getPreChangeType() {
    return preChangeType;
  }

  public void setPreChangeType(int preChangeType) {
    this.preChangeType = preChangeType;
  }

  public String getGroupid() {
    return groupid;
  }

  public void setGroupid(String groupid) {
    this.groupid = groupid;
  }

  public String getPub_duration() {
    return pub_duration;
  }

  public void setPub_duration(String pub_duration) {
    this.pub_duration = pub_duration;
  }

  public String getWork_duration() {
    return work_duration;
  }

  public void setWork_duration(String work_duration) {
    this.work_duration = work_duration;
  }

  public String getRest_duration() {
    return rest_duration;
  }

  public void setRest_duration(String rest_duration) {
    this.rest_duration = rest_duration;
  }

  public String getTimeselection() {
    return timeselection;
  }

  public void setTimeselection(String timeselection) {
    this.timeselection = timeselection;
  }

  public ProcessChangeSplitBean getProcessChangeSplitBean() {
    return processChangeSplitBean;
  }

  public void setProcessChangeSplitBean(
      ProcessChangeSplitBean processChangeSplitBean) {
    this.processChangeSplitBean = processChangeSplitBean;
  }

  public String getIscompanion() {
    return iscompanion;
  }

  public void setIscompanion(String iscompanion) {
    this.iscompanion = iscompanion;
  }

  public String getFilterholidays() {
    return filterholidays;
  }

  public void setFilterholidays(String filterholidays) {
    this.filterholidays = filterholidays;
  }

  public List<OvertimeBalanceTimeBean> getOvertimeBalanceTimeBeans() {
    return overtimeBalanceTimeBeans;
  }

  public void setOvertimeBalanceTimeBeans(
      List<OvertimeBalanceTimeBean> overtimeBalanceTimeBeans) {
    this.overtimeBalanceTimeBeans = overtimeBalanceTimeBeans;
  }

  public String getOvertime_type() {
    return overtime_type;
  }

  public void setOvertime_type(String overtime_type) {
    this.overtime_type = overtime_type;
  }

  public String getConversion() {
    return conversion;
  }

  public void setConversion(String conversion) {
    this.conversion = conversion;
  }

  public String getReduceMonth() {
    return reduceMonth;
  }

  public void setReduceMonth(String reduceMonth) {
    this.reduceMonth = reduceMonth;
  }
}

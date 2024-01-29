<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.engine.kq.biz.KQOvertimeRulesBiz,com.engine.kq.enums.KqSplitFlowTypeEnum,com.engine.kq.wfset.attendance.domain.WorkflowBase,com.engine.kq.wfset.attendance.manager.WorkflowBaseManager" %>
<%@ page import="com.engine.kq.wfset.util.KQAttFlowCheckUtil"%>
<%@page import="com.engine.kq.wfset.util.SplitSelectSet"%>
<%@ page import="java.util.Map" %>
<%@ page import="weaver.general.Util" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="weaver.systeminfo.SystemEnv" %>
<%@ page import="com.engine.kq.biz.KQTimeSelectionComInfo" %>
<jsp:useBean id="strUtil" class="weaver.common.StringUtil" scope="page" />
<jsp:useBean id="dateUtil" class="weaver.common.DateUtil" scope="page" />
<jsp:useBean id="RecordSet" class="weaver.conn.RecordSet" scope="page" />
<jsp:useBean id="attProcSetManager" class="com.engine.kq.wfset.attendance.manager.HrmAttProcSetManager" scope="page" />
<jsp:useBean id="TempleService" class="weaver.temple.TempleService" scope="page" />
<%
    User user = (User)request.getSession(true).getAttribute("weaver_user@bean");
    int nodetype = Util.getIntValue(request.getParameter("nodetype"), 0);
    int requestid = Util.getIntValue(request.getParameter("requestid"));
    int workflowid = Util.getIntValue(request.getParameter("workflowid"), 0);
    int nodeid = Util.getIntValue(request.getParameter("nodeid"), 0);
    int formid = Util.getIntValue(request.getParameter("formid"));
    int userid = Util.getIntValue(request.getParameter("userid"));
    String creater = Util.null2s(request.getParameter("creater"), String.valueOf(userid));
    WorkflowBaseManager workflowBaseManager = new WorkflowBaseManager();
    if(formid == -1) {
        WorkflowBase bean = workflowBaseManager.get(workflowid);
        formid = bean == null ? -1 : bean.getFormid();
    }
    System.out.println("formid:"+formid);
    Map<String,Object> result = attProcSetManager.getFieldList(3,workflowid, formid);
    String[] fieldList = (String[])result.get("fields");
    String usedetail = Util.null2String(result.get("usedetail"));
    String detailtablename = Util.null2String(result.get("detailtablename"));

    String attid = Util.null2String(result.get("attid"));

    String isAttOk = "1";
    String msgAttError = "";
    boolean isKQFlow = KQAttFlowCheckUtil.isKQFlow(result);
    if(!isKQFlow){
        return ;
    }
    Map<String,String> check = KQAttFlowCheckUtil.checkAttFlow(result, KqSplitFlowTypeEnum.OVERTIME);
	
    isAttOk = Util.null2s(check.get("isAttOk"),"0");
    msgAttError = Util.null2s(check.get("msgAttError"),"考勤流程设置有误");

    int _customAddFun = 0;
    String detail_dt = "1";
    int detailLen = detailtablename.lastIndexOf("_");
    int detailIndex = Util.getIntValue(Util.null2String(result.get("detailIndex")),1);
    int tablelength = detailtablename.length();
    int index_dt = detailtablename.indexOf("dt");
    if(tablelength>0 && index_dt>0){
        detailIndex = Util.getIntValue(Util.null2String(detailtablename.substring(index_dt+2,tablelength)),1);
    }
    if(detailIndex > 0){
        detail_dt = ""+detailIndex;
        _customAddFun = Util.getIntValue(detail_dt)-1;
    }

    String halfFromSel = "[]";
    String halfToSel = "[]";
    String wholeFromSel = "";
    String wholeToSel = "";

    boolean isHalf = false;
    boolean isWhole = false;
    int minimumUnit = KQOvertimeRulesBiz.getMinimumUnit();
    String timeselection = KQOvertimeRulesBiz.getTimeselection();

    String forenoon_start = SplitSelectSet.forenoon_start;
    String forenoon_end = SplitSelectSet.forenoon_end;
    String afternoon_start = SplitSelectSet.afternoon_start;
    String afternoon_end = SplitSelectSet.afternoon_end;
    String daylong_start = SplitSelectSet.daylong_start;
    String daylong_end = SplitSelectSet.daylong_end;

    if(!"2".equalsIgnoreCase(timeselection)){
        KQTimeSelectionComInfo kqTimeSelectionComInfo = new KQTimeSelectionComInfo();
        String selectiontype = ""+KqSplitFlowTypeEnum.OVERTIME.getFlowtype();
        Map<String,String> map = kqTimeSelectionComInfo.getTimeselections(selectiontype,"0",minimumUnit+"");

        String am  = SystemEnv.getHtmlLabelName(16689, user.getLanguage());
        String pm  = SystemEnv.getHtmlLabelName(16690, user.getLanguage());
        if(!map.isEmpty()){
            am = Util.null2String(map.get("half_on"));
            pm = Util.null2String(map.get("half_off"));
            if(am.length() == 0){
                am = SystemEnv.getHtmlLabelName(16689, user.getLanguage());
            }
            if(pm.length() == 0){
                pm = SystemEnv.getHtmlLabelName(16690, user.getLanguage());
            }
        }

        halfFromSel = "[{key:'"+forenoon_start+"',showname:'"+am+"'},{key:'"+forenoon_end+"',showname:'"+pm+"'}]";
        halfToSel = "[{key:'"+afternoon_start+"',showname:'"+am+"'},{key:'"+afternoon_end+"',showname:'"+pm+"'}]";

        if(minimumUnit == 2){
            isHalf = true;
        }
    }
    wholeFromSel = "[{key:'"+daylong_start+"',showname:'"+SystemEnv.getHtmlLabelName(390728, user.getLanguage())+"'}]";
    wholeToSel = "[{key:'"+daylong_end+"',showname:'"+SystemEnv.getHtmlLabelName(390728, user.getLanguage())+"'}]";
    if(minimumUnit == 4){
        isWhole = true;
    }

    String currentdate = Util.null2s(request.getParameter("currentdate"), dateUtil.getCurrentDate());
    String f_weaver_belongto_userid = Util.null2s(request.getParameter("f_weaver_belongto_userid"),"");
    String f_weaver_belongto_usertype = Util.null2s(request.getParameter("f_weaver_belongto_usertype"),"");
    String currentnodetype = "";
    if(requestid > 0){
        String sql = "select currentnodetype from workflow_requestbase where requestid = "+requestid;
        RecordSet.executeQuery(sql);
        if(RecordSet.next()){
            currentnodetype = RecordSet.getString("currentnodetype");
        }
    }else{
        currentnodetype = nodetype+"";
    }
    double hours = TempleService.getHours();
    String fields = TempleService.getfield();
    String field = fields.split("_")[0];
    String select = fields.split("_")[1];
%>
<script  src="<%=weaver.general.GCONST.getContextPath()%>/workflow/request/ext4e9/common.js"></script>
<script >
  var isMobile = WfForm.isMobile();
  var isAttOk = "<%=isAttOk%>";
  var usedetail = "<%=usedetail%>";
  var formid = "<%=formid%>";
  var detail_dt = "<%=detail_dt%>";
  var _customAddFun = "<%=_customAddFun%>";
  var _field_resourceId = "<%=fieldList[0]%>";
  var _field_fromDate = "<%=fieldList[2]%>";
  var _field_fromTime = "<%=fieldList[3]%>";
  var _field_toDate = "<%=fieldList[4]%>";
  var _field_toTime = "<%=fieldList[5]%>";
  var _field_duration = "<%=fieldList[6]%>";
  var _field_overtime_type = "<%=fieldList[7]%>";
  var f_weaver_belongto_userid = "<%=f_weaver_belongto_userid%>";
  var f_weaver_belongto_usertype = "<%=f_weaver_belongto_usertype%>";

  var useHalf = "<%=isHalf?"1":"0"%>";
  var useWhole = "<%=isWhole?"1":"0"%>";
  var halfFromSel = <%=halfFromSel%>;
  var halfToSel = <%=halfToSel%>;
  var wholeFromSel = <%=wholeFromSel%>;
  var wholeToSel = <%=wholeToSel%>;
  var msgAttError = "<%=msgAttError%>";
  var requestid = "<%=requestid%>";
  var currentnodetype = "<%=currentnodetype%>";
  var attid = "<%=attid%>";
  var isWorkDay ;
  var days;//加班天数
  var hours = "<%=hours%>";
  var field = "<%=field%>";//联动的日期
  var select = "<%=select%>";//下拉框

  //避免频繁ajax请求的时间戳对象
  var _duration_stamp = {};

  jQuery(document).ready(function(){
    try {
      if(usedetail != "1"){
        if(_field_duration != "") {
          WfForm.changeFieldAttr(_field_duration, 1);
        }
        if(_field_fromTime != "") {
          var _val_json = {};
          if(useHalf == "1"){
            _val_json[_field_fromTime] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfFromSel};
            WfForm.changeMoreField({}, _val_json);
          }else if(useWhole == "1"){
            _val_json[_field_fromTime] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeFromSel};
            WfForm.changeMoreField({}, _val_json);
          }
        }
        if(_field_toTime != "") {
          var _val_json = {};
          if(useHalf == "1"){
            _val_json[_field_toTime] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfToSel};
            WfForm.changeMoreField({}, _val_json);
          }else if(useWhole == "1"){
            _val_json[_field_toTime] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeToSel};
            WfForm.changeMoreField({}, _val_json);
          }
        }
        getLoop_WorkDuration("",0,[]);
      }else{
        var detailAllRowIndexStr = WfForm.getDetailAllRowIndexStr("detail_"+detail_dt);
        if (detailAllRowIndexStr != "") {
          var detailAllRowIndexStr_array = detailAllRowIndexStr.split(",");
          for (var rowIdx = 0; rowIdx < detailAllRowIndexStr_array.length; rowIdx++) {
            var idx = detailAllRowIndexStr_array[rowIdx];
            var _key = _field_fromTime+"_"+idx;
            var _key1 = _field_toTime+"_"+idx;
            var _key2 = _field_duration+"_"+idx;
            var _val_json = {};
            if(useHalf == "1"){
              _val_json[_key] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfFromSel};
              _val_json[_key1] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfToSel};
              WfForm.changeMoreField({}, _val_json);
            }else if(useWhole == "1"){
              _val_json[_key] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeFromSel};
              _val_json[_key1] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeToSel};
              WfForm.changeMoreField({}, _val_json);
            }
            WfForm.changeFieldAttr(_key2, 1);
          }
          var loop_i=0;
          getLoop_WorkDuration(detailAllRowIndexStr_array[loop_i],loop_i,detailAllRowIndexStr_array);
        }
      }
      var changeFields =_field_resourceId+","+_field_fromDate+","+_field_fromTime
          +","+_field_toDate+","+_field_toTime;

      if(usedetail == "1"){
        WfForm.bindDetailFieldChangeEvent(changeFields, function(id,rowIndex,value){
          _wfbrowvalue_onchange_detail(id,rowIndex,value);
        });
      }else{
        WfForm.bindFieldChangeEvent(changeFields, function(obj,id,value){
          _wfbrowvalue_onchange(obj,id,value);
        });
      }

      //绑定提交前事件
      WfForm.registerCheckEvent(WfForm.OPER_SUBMIT+","+WfForm.OPER_SUBMITCONFIRM,function(callback){
        doBeforeSubmit_hrm(callback);
      });

      if(usedetail == "1"){
        var f = "_customAddFun"+_customAddFun;
        window[f] = function (addIndexStr) {
          if(addIndexStr !=undefined && addIndexStr != null){
            var _key = _field_fromTime+"_"+addIndexStr;
            var _key1 = _field_toTime+"_"+addIndexStr;
            var _key2 = _field_duration+"_"+addIndexStr;
            var _val_json = {};
            if(useHalf == "1"){
              _val_json[_key] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfFromSel};
              _val_json[_key1] = {changeFieldType:"5-1",changeFieldTypeSelectOption:halfToSel};
              WfForm.changeMoreField({}, _val_json);
            }else if(useWhole == "1"){
              _val_json[_key] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeFromSel};
              _val_json[_key1] = {changeFieldType:"5-1",changeFieldTypeSelectOption:wholeToSel};
              WfForm.changeMoreField({}, _val_json);
            }
            var _val_json = {};
            var _viewAttr_json = {};
            _viewAttr_json[_key2] = {viewAttr:1};
            WfForm.changeMoreField(_val_json,_viewAttr_json);
          }
        }
      }
    }catch (e) {
      return;
    }

  });

  /**
   * 明细字段变化触发事件
   * @param obj
   * @param fieldid
   * @param rowindex
   * @private
   */
  function _wfbrowvalue_onchange_detail(id, rowIndex, value) {
    if(id == _field_fromDate || id == _field_fromTime || id == _field_toDate
        || id == _field_toTime || id == _field_resourceId|| id == _field_overtime_type){
      getLoop_WorkDuration(rowIndex,0,[]);
    }
  }
  /**
   * 字段变化触发事件
   * @param obj
   * @param fieldid
   * @param rowindex
   * @private
   */
  function _wfbrowvalue_onchange(obj,id,value) {
    if(id == _field_fromDate || id == _field_fromTime || id == _field_toDate
        || id == _field_toTime || id == _field_resourceId|| id == _field_overtime_type){
      getLoop_WorkDuration("",0,[]);
    }
  }
    function setValue2(){
      var resourceId = WfForm.getFieldValue(_field_resourceId);
      var fromdate = WfForm.getFieldValue(_field_fromDate);
      if(fromdate != ''){
        jQuery.ajax({
          url : "/workflow/request/BillBoHaiLeaveXMLHTTP.jsp",
          type : "post",
          processData : false,
          data : "operation=isWorkDay&fromDate="+fromdate+"&resourceId="+resourceId,
          dataType : "json",
          success: function do4Success(data){
              isWorkDay=data.isWorkDay;
          },
	  error: function(XMLHttpRequest, textStatus, errorThrown) {
		//console.log(XMLHttpRequest);
		//console.log(textStatus);
		//console.log(errorThrown);
	  },
	  
        });
      }
  }

  /**
   * 做个递归请求 防止循环ajax请求导致返回值错乱
   * @param rowIndex
   * @param identity
   * @param detailAllRowIndexStr_array
   */
  function getLoop_WorkDuration(rowIndex,identity,detailAllRowIndexStr_array){
      var aa = WfForm.getFieldValue(_field_fromDate);
      setValue2(aa);
      return;
    var _field_field_resourceId = _field_resourceId;
    var _field_field_fromDate = _field_fromDate;
    var _field_field_fromTime = _field_fromTime;
    var _field_field_toDate = _field_toDate;
    var _field_field_toTime = _field_toTime;
    var _field_field_duration = _field_duration;
    var _field_field_overtime_type = _field_overtime_type;
    if(rowIndex !=undefined && rowIndex != null && rowIndex != ""){
      _field_field_resourceId += "_"+rowIndex;
      _field_field_fromDate += "_"+rowIndex;
      _field_field_fromTime += "_"+rowIndex;
      _field_field_toDate += "_"+rowIndex;
      _field_field_toTime += "_"+rowIndex;
      _field_field_duration += "_"+rowIndex;
      _field_field_overtime_type += "_"+rowIndex;
    }
    var _field_resourceIdVal = null2String(WfForm.getFieldValue(_field_field_resourceId));
    var _field_fromDateVal = null2String(WfForm.getFieldValue(_field_field_fromDate));
    var _field_fromTimeVal = null2String(WfForm.getFieldValue(_field_field_fromTime));
    var _field_toDateVal = null2String(WfForm.getFieldValue(_field_field_toDate));
    var _field_toTimeVal = null2String(WfForm.getFieldValue(_field_field_toTime));
    var _field_overtime_typeVal = null2String(WfForm.getFieldValue(_field_field_overtime_type));

    if(currentnodetype == 3){
      return ;
    }
    if(!_field_resourceIdVal || !_field_fromDateVal || !_field_toDateVal){
      return ;
    }

    var duration_stamp = new Date().getTime();
    _duration_stamp[_field_field_duration] = duration_stamp;

    var _data = "resourceId="+_field_resourceIdVal+"&fromDate="+_field_fromDateVal
        +"&fromTime="+_field_fromTimeVal+"&toDate="+_field_toDateVal+"&toTime="+_field_toTimeVal
        +"&timestamp="+duration_stamp+"&overtime_type="+_field_overtime_typeVal;

    jQuery.ajax({
      url : "<%=weaver.general.GCONST.getContextPath()%>/api/hrm/kq/attendanceEvent/getOverTimeDuration",
      type : "post",
      processData : false,
      data : _data,
      dataType : "json",
      success: function do4Success(data){
        if(identity < detailAllRowIndexStr_array.length){
          getLoop_WorkDuration(detailAllRowIndexStr_array[identity+1],identity+1,detailAllRowIndexStr_array);
        }
        if(data != null && data.status == "1"){
          var result_timestamp = data.timestamp;
          if(_duration_stamp && _duration_stamp[_field_field_duration]){
            if(result_timestamp < _duration_stamp[_field_field_duration]){
              //如果频繁发送ajax请求，以最后一次的ajax请求为准
            }else{
              var _key = _field_field_duration;
              var _val_json = {};
              var _viewAttr_json = {};
              _val_json[_key] = {value:data.duration};
              _viewAttr_json[_key] = {viewAttr:1};
              WfForm.changeMoreField(_val_json,_viewAttr_json);
            }
          }else{
            var _key = _field_field_duration;
            var _val_json = {};
            var _viewAttr_json = {};
            _val_json[_key] = {value:data.duration};
            _viewAttr_json[_key] = {viewAttr:1};
            WfForm.changeMoreField(_val_json,_viewAttr_json);
          }

        }else{
          if(data.message){
            WfForm.showMessage(data.message);
          }
        }
      }
    });
  }

  //提交事件前出发函数
  function doBeforeSubmit_hrm(callback){
    try{
      WfForm.controlBtnDisabled(true);//把流程中的按钮置灰
      if("0" == isAttOk){
        WfForm.showMessage(msgAttError);
        WfForm.controlBtnDisabled(false);
        return;
      }
        // alert("aa = "+aa+",bb="+bb+",cc="+cc+",dd="+dd);
        //下拉框值判断是否可以提交
        var aa = WfForm.getFieldValue(_field_fromDate);
        console.log('select', select);
        var svalue = WfForm.getFieldValue(select);
        console.log('svalue', svalue);
        if(svalue=="0")days="0.5";
        if(svalue=="1")days="1.0";
        //判断是否工作日
        setValue2(aa);
        if(svalue==""){
            WfForm.showMessage("请选择预计加班天数!");  
            WfForm.controlBtnDisabled(false);
            return;
        }

        if(isWorkDay=="true"){
            WfForm.showMessage("工作日不算加班!");  
            WfForm.controlBtnDisabled(false);
            return;
        }
        if(days*hours < 3){
            WfForm.showMessage("加班时间少于3小时不算加班!");  
            WfForm.controlBtnDisabled(false);
            return;
        }
      var checkRuleData = {};
      var checkDurationData = {};
      if(usedetail == "1"){
        var detailAllRowIndexStr = WfForm.getDetailAllRowIndexStr("detail_"+detail_dt);
        if(detailAllRowIndexStr != ""){
          var detailAllRowIndexStr_array = detailAllRowIndexStr.split(",");
          for(var rowIdx = 0; rowIdx < detailAllRowIndexStr_array.length; rowIdx++){
            var idx = detailAllRowIndexStr_array[rowIdx];
            var _field_resourceId_val = WfForm.getFieldValue(_field_resourceId+"_"+idx);
            var _field_fromDate_val = WfForm.getFieldValue(_field_fromDate+"_"+idx);
            var _field_fromTime_val = WfForm.getFieldValue(_field_fromTime+"_"+idx);
            var _field_toDate_val = WfForm.getFieldValue(_field_toDate+"_"+idx);
            var _field_toTime_val = WfForm.getFieldValue(_field_toTime+"_"+idx);
			
			//移动端增加半天和全天时间校验byzqb
              if(useHalf == "1"){
                  if(_field_fromTime_val && _field_fromTime_val != "" && _field_fromTime_val != "08:00" && _field_fromTime_val != "13:00"){
                      WfForm.showMessage("开始时间不符合半天规则,请重新选择");  //开始时间不符合半天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

                  if(_field_toTime_val && _field_toTime_val != "" && _field_toTime_val != "13:00" && _field_toTime_val != "18:00"){
                      WfForm.showMessage("结束时间不符合半天规则,请重新选择");  //结束时间不符合半天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }
              }else if(useWhole == "1"){
                  if(_field_fromTime_val && _field_fromTime_val != "" && _field_fromTime_val != "08:00"){
                      WfForm.showMessage("开始时间不符合全天规则,请重新选择");  //开始时间不符合全天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

                  if(_field_toTime_val && _field_toTime_val != "" && _field_toTime_val != "18:00"){
                      WfForm.showMessage("结束时间不符合全天规则,请重新选择");  //结束时间不符合全天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

              }
			
			
            if(!DateCheck(_field_fromDate_val,_field_fromTime_val,_field_toDate_val,_field_toTime_val,"<%=SystemEnv.getHtmlLabelName(15273,user.getLanguage())%>")){
              WfForm.controlBtnDisabled(false);
              return;
            }
            var _field_duration_val = WfForm.getFieldValue(_field_duration+"_"+idx);
            if(!durationCheck(_field_duration_val,"<%=SystemEnv.getHtmlLabelName(391102,user.getLanguage())%>")){
              WfForm.controlBtnDisabled(false);
              return;
            }
            checkRuleData[rowIdx] = _field_resourceId_val+"_"+_field_fromDate_val+"_"+_field_fromTime_val+"_"+_field_toDate_val+"_"+_field_toTime_val;
            checkDurationData[rowIdx] = _field_resourceId_val+"_"+currentnodetype+"_"+_field_duration_val;
          }
        }
      }else{
        var _field_resourceId_val = WfForm.getFieldValue(_field_resourceId);
        var _field_fromDate_val = WfForm.getFieldValue(_field_fromDate);
        var _field_fromTime_val = WfForm.getFieldValue(_field_fromTime);
        var _field_toDate_val = WfForm.getFieldValue(_field_toDate);
        var _field_toTime_val = WfForm.getFieldValue(_field_toTime);
		
		//移动端增加半天和全天时间校验byzqb
              if(useHalf == "1"){
                  if(_field_fromTime_val && _field_fromTime_val != "" && _field_fromTime_val != "08:00" && _field_fromTime_val != "13:00"){
                      WfForm.showMessage("开始时间不符合半天规则,请重新选择");  //开始时间不符合半天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

                  if(_field_toTime_val && _field_toTime_val != "" && _field_toTime_val != "13:00" && _field_toTime_val != "18:00"){
                      WfForm.showMessage("结束时间不符合半天规则,请重新选择");  //结束时间不符合半天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }
              }else if(useWhole == "1"){
                  if(_field_fromTime_val && _field_fromTime_val != "" && _field_fromTime_val != "08:00"){
                      WfForm.showMessage("开始时间不符合全天规则,请重新选择");  //开始时间不符合全天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

                  if(_field_toTime_val && _field_toTime_val != "" && _field_toTime_val != "18:00"){
                      WfForm.showMessage("结束时间不符合全天规则,请重新选择");  //结束时间不符合全天规则,请重新选择！
                      WfForm.controlBtnDisabled(false);
                      return;
                  }

              }
		
		
        if(!DateCheck(_field_fromDate_val,_field_fromTime_val,_field_toDate_val,_field_toTime_val,"<%=SystemEnv.getHtmlLabelName(15273,user.getLanguage())%>")){
          WfForm.controlBtnDisabled(false);
          return;
        }
        var _field_duration_val = WfForm.getFieldValue(_field_duration);
        /*if(!durationCheck(_field_duration_val,"<%=SystemEnv.getHtmlLabelName(391102,user.getLanguage())%>")){
          WfForm.controlBtnDisabled(false);
          return;
        }*/
        checkRuleData[0] = _field_resourceId_val+"_"+_field_fromDate_val+"_"+_field_fromTime_val+"_"+_field_toDate_val+"_"+_field_toTime_val;
        checkDurationData[0] = _field_resourceId_val+"_"+currentnodetype+"_"+_field_duration_val;
      }
      var checkRuleData2json=JSON.stringify(checkRuleData);
      var checkDurationData2json=JSON.stringify(checkDurationData);
      var _data = "checkRuleData2json="+checkRuleData2json+"&requestid="+requestid+"&currentnodetype="+currentnodetype+"&attid="+attid+"&checkDurationData2json="+checkDurationData2json;

      jQuery.ajax({
        url : "<%=weaver.general.GCONST.getContextPath()%>/api/hrm/kq/attendanceEvent/checkOvertime",
        type : "post",
        processData : false,
        data : _data,
        dataType : "json",
        success: function do4Success(data){
          if(data != null && data.status == "1"){
            WfForm.controlBtnDisabled(false);
            callback(); //继续提交需调用callback，不调用代表阻断
            return;
          }else{
            var errorInfo = data.message;
            if(data.status == -2){
              if(isMobile){
                if(data.mobile_message) {
                  errorInfo = data.mobile_message;
                }
              }
              WfForm.showConfirm(errorInfo,function(){
                WfForm.controlBtnDisabled(false);
                callback(); //继续提交需调用callback，不调用代表阻断
                return;
              },function () {
                WfForm.controlBtnDisabled(false);
                return;
              });
            }else {
              if(isMobile){
                var mobile_errorInfo = errorInfo;
                if(data.mobile_message){
                  mobile_errorInfo = data.mobile_message;
                }
                if(window.showMsg4kqflowMobile){
                  window.showMsg4kqflowMobile(mobile_errorInfo);
                }else{
                  WfForm.showMessage(mobile_errorInfo);
                }
              }else{
                if(window.showMsg4kqflow){
                  window.showMsg4kqflow(errorInfo);
                }else{
                  WfForm.showMessage(errorInfo);
                }
              }
              WfForm.controlBtnDisabled(false);
              return;
            }
          }
        }
      });
    }catch(ex1){
      WfForm.controlBtnDisabled(false);//取消流程中的按钮置灰
      return;
    }
  }

  function null2String(s){
    if(!s){
      return "";
    }
    return s;
  }

  function DateCheck(fromDate,fromTime,toDate,toTime,msg){

    var begin = new Date(fromDate.replace(/\-/g, "\/"));
    var end = new Date(toDate.replace(/\-/g, "\/"));
    if(fromTime != "" && toTime != ""){
      begin = new Date(fromDate.replace(/\-/g, "\/")+" "+fromTime+":00");
      end = new Date(toDate.replace(/\-/g, "\/")+" "+toTime+":00");
      if(fromDate!=""&&toDate!=""&&begin >end)
      {
        WfForm.showMessage(msg);
        return false;
      }
    }else{
      if(fromDate!=""&&toDate!=""&&begin >end)
      {
        WfForm.showMessage(msg);
        return false;
      }
    }
    return true;
  }

  function durationCheck(duration,msg){
    if(duration){
      if(parseFloat(duration) <= 0){
        WfForm.showMessage(msg);
        return false;
      }
    }else{
      WfForm.showMessage(msg);
      return false;
    }
    return true;
  }
</script>

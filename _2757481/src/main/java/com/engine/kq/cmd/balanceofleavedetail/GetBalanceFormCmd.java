package com.engine.kq.cmd.balanceofleavedetail;

import com.api.browser.bean.SearchConditionItem;
import com.api.hrm.bean.HrmFieldBean;
import com.api.hrm.util.HrmFieldSearchConditionComInfo;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQLeaveRulesComInfo;
import com.engine.kq.biz.KQLeaveRulesDetailComInfo;
import com.engine.kq.biz.KQUnitBiz;
import weaver.conn.RecordSet;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.SystemEnv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 获取加班调休明细的编辑表单
 */
public class GetBalanceFormCmd extends AbstractCommonCommand<Map<String, Object>> {

    public GetBalanceFormCmd(Map<String, Object> params, User user) {
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
            String bulkEdit = Util.null2String(params.get("bulkEdit"));
            boolean canEdit = HrmUserVarify.checkUserRight("KQLeaveRulesEdit:Edit", user);//是否具有编辑权限
            if (!canEdit) {
                resultMap.put("status", "-1");
                resultMap.put("message", SystemEnv.getHtmlLabelName(2012, user.getLanguage()));
                return resultMap;
            }

            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

            String id = Util.null2String(params.get("id"));//这条明细记录的ID

            String resourceId = "";//人员ID
            String leaveRulesId = "";//假期类型的ID
            String belongYear = "";//年度
            String belongMonth = "";//期间
            String overtimeType = "";//加班类型
            String baseAmount = "0.00";//基数
            String extraAmount = "0.00";//额外
            String tiaoxiuamount = "0.00";//加班生成调休
            String usedAmount = "0.00";//已休
            String invalidAmount = "0.00";//作废
            String effectiveDate = "";//生效日期
            String expirationDate = "";//失效日期

            if (!id.equals("")) {
                String sql = "select * from KQ_BalanceOfLeave where (isDelete is null or isDelete<>1) and id=" + id;
                RecordSet recordSet = new RecordSet();
                recordSet.executeQuery(sql);
                if (recordSet.next()) {
                    resourceId = recordSet.getString("resourceId");
                    leaveRulesId = recordSet.getString("leaveRulesId");
                    belongYear = recordSet.getString("belongYear");
                    belongMonth = recordSet.getString("belongMonth");
                    overtimeType = recordSet.getString("overtimeType");
                    baseAmount = recordSet.getString("baseAmount");
                    extraAmount = recordSet.getString("extraAmount");
                    tiaoxiuamount = recordSet.getString("tiaoxiuamount");
                    usedAmount = recordSet.getString("usedAmount");
                    effectiveDate = recordSet.getString("effectiveDate");
                    expirationDate = recordSet.getString("expirationDate");
                }
            }

            //假期类型的缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            String minimumUnit = rulesComInfo.getMinimumUnit(leaveRulesId);
            String unitLabel = "";
            if (KQUnitBiz.isLeaveHour(minimumUnit)) {
                unitLabel = "389326";
            } else {
                unitLabel = "389325";
            }
            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //假期规则的缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            int decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(leaveRulesId, subcompanyId), 2);

            BigDecimal _totalAmount = new BigDecimal(Util.getDoubleValue(baseAmount, 0.00) + Util.getDoubleValue(extraAmount, 0));
            BigDecimal _usedAmount = new BigDecimal(Util.getDoubleValue(usedAmount, 0.00));
            BigDecimal _tiaoxiuamount = new BigDecimal(Util.getDoubleValue(tiaoxiuamount, 0.00)).setScale(2,RoundingMode.HALF_UP);
            String _invalidAmount = "0.00";
            String _restAmount = "0.00";
            /*如果还处于有效期内，则失效的天数或者小时数为0*/
            if (expirationDate == null || "".equals(expirationDate) || expirationDate.compareTo(currentDate) >= 0) {
                _invalidAmount = "0.00";
                _restAmount = _totalAmount.add(_tiaoxiuamount).subtract(_usedAmount).setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
            } else {
                _invalidAmount = _totalAmount.add(_tiaoxiuamount).subtract(_usedAmount).setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
                _restAmount = "0.00";
            }

            List<Map<String, Object>> groupList = new ArrayList<Map<String, Object>>();
            Map<String, Object> groupItem = new HashMap<String, Object>();
            List<Object> itemList = new ArrayList<Object>();
            List<Object> itemListNew = new ArrayList<Object>();
            HrmFieldSearchConditionComInfo hrmFieldSearchConditionComInfo = new HrmFieldSearchConditionComInfo();
            SearchConditionItem searchConditionItem = null;
            HrmFieldBean hrmFieldBean = null;

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("resourceId");
            hrmFieldBean.setFieldlabel("413");
            hrmFieldBean.setFieldhtmltype("3");
            hrmFieldBean.setType("1");
            hrmFieldBean.setFieldvalue(resourceId);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("belongYear");
            hrmFieldBean.setFieldlabel("15933");
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("1");
            hrmFieldBean.setFieldvalue(belongYear);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("belongMonth");
            hrmFieldBean.setFieldlabel("887");
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("1");
            hrmFieldBean.setFieldvalue(belongMonth);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

           /* hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("overtimeType");
            hrmFieldBean.setFieldlabel("6159");
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("1");
            hrmFieldBean.setFieldvalue(overtimeType);
            hrmFieldBean.setIsFormField(true);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            itemList.add(searchConditionItem);*/

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("totalAmount");
            hrmFieldBean.setFieldlabel("130286" + "," + unitLabel);
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(_totalAmount.setScale(decimalDigit, RoundingMode.HALF_UP).doubleValue());
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(3);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            searchConditionItem.setRules("required|numeric");
            searchConditionItem.setPrecision(2);
            searchConditionItem.setMin("0");
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("usedAmount");
            hrmFieldBean.setFieldlabel("26642" + "," + unitLabel);
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(usedAmount);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(3);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            searchConditionItem.setRules("required|numeric");
            searchConditionItem.setPrecision(2);
            searchConditionItem.setMin("0");
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("invalidAmount");
            hrmFieldBean.setFieldlabel("389611" + "," + unitLabel);
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(_invalidAmount);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            searchConditionItem.setRules("required|numeric");
            searchConditionItem.setPrecision(2);
            searchConditionItem.setMin("0");
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("tiaoxiuamount");
            hrmFieldBean.setFieldlabel("126739" + "," + unitLabel);
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(_tiaoxiuamount);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            searchConditionItem.setRules("required|numeric");
            searchConditionItem.setPrecision(2);
            searchConditionItem.setMin("0");
            if (hrmFieldBean.getViewAttr() == 1) {
              Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
              OtherParamsMap.put("hasBorder", true);
              searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("restAmount");
            hrmFieldBean.setFieldlabel("25723" + "," + unitLabel);
            hrmFieldBean.setFieldhtmltype("1");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(_restAmount);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            searchConditionItem.setRules("required|numeric");
            searchConditionItem.setPrecision(2);
            searchConditionItem.setMin("0");
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("effectiveDate");
            hrmFieldBean.setFieldlabel("508169");
            hrmFieldBean.setFieldhtmltype("3");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(effectiveDate);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(1);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
            if (hrmFieldBean.getViewAttr() == 1) {
                Map<String, Object> OtherParamsMap = new HashMap<String, Object>();
                OtherParamsMap.put("hasBorder", true);
                searchConditionItem.setOtherParams(OtherParamsMap);
            }
            itemList.add(searchConditionItem);

            hrmFieldBean = new HrmFieldBean();
            hrmFieldBean.setFieldname("expirationDate");
            hrmFieldBean.setFieldlabel("19547");
            hrmFieldBean.setFieldhtmltype("3");
            hrmFieldBean.setType("2");
            hrmFieldBean.setFieldvalue(expirationDate);
            hrmFieldBean.setIsFormField(true);
            hrmFieldBean.setViewAttr(3);
            searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
			searchConditionItem.setRules("required|string");
            itemList.add(searchConditionItem);
            itemListNew.add(searchConditionItem);

            if ("1".equals(bulkEdit)){
                groupItem.put("items", itemListNew);
                groupList.add(groupItem);
            }else {
                groupItem.put("items", itemList);
                groupList.add(groupItem);
            }
            resultMap.put("condition", groupList);
        } catch (Exception e) {
            writeLog(e);
            resultMap.put("status", "-1");
            resultMap.put("message", e.getMessage());
        }
        return resultMap;
    }
}

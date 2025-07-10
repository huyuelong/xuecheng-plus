package com.xuecheng.orders.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.IdWorkerUtils;
import com.xuecheng.base.utils.QRCodeUtil;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xuecheng.orders.config.AlipayConfig;
import com.xuecheng.orders.config.PayNotifyConfig;
import com.xuecheng.orders.mapper.XcOrdersGoodsMapper;
import com.xuecheng.orders.mapper.XcOrdersMapper;
import com.xuecheng.orders.mapper.XcPayRecordMapper;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcOrders;
import com.xuecheng.orders.model.po.XcOrdersGoods;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private XcOrdersMapper xcOrdersMapper;

    @Autowired
    private XcOrdersGoodsMapper xcOrdersGoodsMapper;

    @Autowired
    private XcPayRecordMapper payRecordMapper;

    @Autowired
    private OrderServiceImpl currentProxy;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqMessageService mqMessageService;

    @Value("${pay.qrcodeurl}")
    String qrcodeurl;

    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;

    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;

    @Transactional
    @Override
    public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto) {

        // 插入订单表，订单主表，订单明细表
        XcOrders xcOrders = saveXcOrders(userId, addOrderDto);

        // 插入支付记录
        XcPayRecord payRecord = createPayRecord(xcOrders);
        Long payNo = payRecord.getPayNo();

        // 生成二维码
        QRCodeUtil qrCodeUtil = new QRCodeUtil();
        // 支付二维码的url
        String url = String.format(qrcodeurl, payNo);
        // 二维码图片
        String qrCode = null;
        try {
            qrCode = qrCodeUtil.createQRCode(url, 200, 200);
        } catch (IOException e) {
            XueChengPlusException.cast("生成二维码失败");
        }

        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecord, payRecordDto);
        payRecordDto.setQrcode(qrCode);

        return payRecordDto;
    }

    @Override
    public XcPayRecord getPayRecordByPayno(String payNo) {
        XcPayRecord xcPayRecord = payRecordMapper.selectOne(new LambdaQueryWrapper<XcPayRecord>().eq(XcPayRecord::getPayNo, payNo));
        return xcPayRecord;
    }

    @Override
    public PayRecordDto queryPayResult(String payNo) {
        // 调用支付宝的接口查询支付结果
        PayStatusDto payStatusDto = queryPayResultFromAlipay(payNo);

        // 拿到支付结果更新支付记录表和订单表的支付状态
        currentProxy.saveAliPayStatus(payStatusDto);
        // 要返回最新的支付记录信息
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecordByPayno, payRecordDto);

        return payRecordDto;
    }

    /**
     * 请求支付宝查询支付结果
     * @param payNo 支付交易号
     * @return 支付结果
     */
    public PayStatusDto queryPayResultFromAlipay(String payNo) {
        AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL, APP_ID, APP_PRIVATE_KEY, "json", AlipayConfig.CHARSET, ALIPAY_PUBLIC_KEY, AlipayConfig.SIGNTYPE); //获得初始化的AlipayClient
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", payNo);
        //bizContent.put("trade_no", "2014112611001004680073956707");
        request.setBizContent(bizContent.toString());
        String body = null;
        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if(!response.isSuccess()) { // 交易不成功
                XueChengPlusException.cast("查询支付宝支付结果失败");
            }
            body = response.getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
            XueChengPlusException.cast("调用支付宝接口查询支付结果出错");
        }
        Map bodyMap = JSON.parseObject(body, Map.class);
        Map alipay_trade_query_response = (Map) bodyMap.get("alipay_trade_query_response");

        // 解析结果
        String trade_no = (String) alipay_trade_query_response.get("trade_no");
        String trade_status = (String) alipay_trade_query_response.get("trade_status");
        String total_amount = (String) alipay_trade_query_response.get("total_amount");
        PayStatusDto payStatusDto = new PayStatusDto();
        payStatusDto.setOut_trade_no(payNo);
        payStatusDto.setTrade_no(trade_no); // 支付宝交易号
        payStatusDto.setTrade_status(trade_status); // 交易状态
        payStatusDto.setApp_id(APP_ID);
        payStatusDto.setTotal_amount(total_amount); // 总金额

        System.out.println(body);
        return payStatusDto;
    }

    /**
     * @description 保存支付宝支付结果
     * @param payStatusDto  支付结果信息
     * @return void
     */
    @Transactional
    public void saveAliPayStatus(PayStatusDto payStatusDto) {

        // 支付记录号
        String payNo = payStatusDto.getOut_trade_no();
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        if (payRecordByPayno == null) {
            XueChengPlusException.cast("支付记录不存在");
        }
        // 拿到相关联的订单id
        Long orderId = payRecordByPayno.getOrderId();
        XcOrders xcOrders = xcOrdersMapper.selectById(orderId);
        if (xcOrders == null) {
            XueChengPlusException.cast("订单不存在");
        }
        // 支付状态
        String statusFromDb = payRecordByPayno.getStatus();
        // 如果数据库支付的状态已经是成功了，不再处理了
        if ("601002".equals(statusFromDb)) {
            XueChengPlusException.cast("订单已支付");
            return;
        }

        // 如果支付成功
        String tradeStatus = payStatusDto.getTrade_status();
        if (tradeStatus.equals("TRADE_SUCCESS")) { // 支付宝返回的信息为支付成功
            // 更新支付记录表的状态为支付成功
            payRecordByPayno.setStatus("601002");
            // 支付宝的订单号
            payRecordByPayno.setOutPayNo(payStatusDto.getTrade_no());
            // 第三方支付渠道编号
            payRecordByPayno.setOutPayChannel("ALIPAY");
            // 支付成功时间
            payRecordByPayno.setPaySuccessTime(LocalDateTime.now());
            payRecordMapper.updateById(payRecordByPayno);

            // 更新订单表的状态为支付成功
            xcOrders.setStatus("600002");
            xcOrdersMapper.updateById(xcOrders);

            // 将消息写到数据库
            MqMessage mqMessage = mqMessageService.addMessage("payresult_notity", xcOrders.getOutBusinessId(), xcOrders.getOrderType(), null);
            // 发送消息
            notifyPayResult(mqMessage);

        }


    }

    @Override
    public void notifyPayResult(MqMessage message) {

        // 消息内容
        String jsonString = JSON.toJSONString(message);
        // 创建一个持久化消息
        MessageBuilderSupport<Message> messageObj = MessageBuilder.withBody(jsonString.getBytes(StandardCharsets.UTF_8)).setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        // 消息id
        Long id = message.getId();

        // 全局消息id
        CorrelationData correlationData = new CorrelationData(id.toString());

        // 使用correlationData指定回调方法
        correlationData.getFuture().addCallback(result -> {
            if (result.isAck()) {
                // 消息成功发送到了交换机
                log.debug("消息发送成功:{}", jsonString);
                // 将消息从数据库表mq_message删除;
                mqMessageService.completed(id);

            } else {
                // 消息发送失败
                log.debug("消息发送失败:{}", jsonString);

            }
        }, ex -> {
            // 发生异常了
            log.debug("消息发送异常:{}", jsonString);
        });
        // 发送消息
        rabbitTemplate.convertAndSend(PayNotifyConfig.PAYNOTIFY_EXCHANGE_FANOUT, "", messageObj, correlationData);

    }


    /**
     * 保存订单记录
     * @param order
     * @return
     */
    public XcPayRecord createPayRecord(XcOrders order) {
        if (order == null) {
            XueChengPlusException.cast("订单不存在");
        }
        if (order.getStatus().equals("600002")) {
            XueChengPlusException.cast("订单已支付");
        }
        XcPayRecord payRecord = new XcPayRecord();
        //生成支付交易流水号
        long payNo = IdWorkerUtils.getInstance().nextId();
        payRecord.setPayNo(payNo);
        payRecord.setOrderId(order.getId());//商品订单号
        payRecord.setOrderName(order.getOrderName());
        payRecord.setTotalPrice(order.getTotalPrice());
        payRecord.setCurrency("CNY");
        payRecord.setCreateDate(LocalDateTime.now());
        payRecord.setStatus("601001");//未支付
        payRecord.setUserId(order.getUserId());
        payRecordMapper.insert(payRecord);
        return payRecord;

    }

    /**
     * 保存订单信息
     * @param userId
     * @param addOrderDto
     * @return
     */
    public XcOrders saveXcOrders(String userId, AddOrderDto addOrderDto) {
        // 插入订单表，订单主表，订单明细表
        // 进行幂等性判断，同一个选课记录只能有一个订单
        XcOrders xcOrder = getOrdersByBusinessId(addOrderDto.getOutBusinessId());
        if (xcOrder != null) {
            return xcOrder;
        }

        // 插入订单主表
        xcOrder = new XcOrders();
        // 使用雪花算法生成订单号
        xcOrder.setId(IdWorkerUtils.getInstance().nextId());
        xcOrder.setTotalPrice(addOrderDto.getTotalPrice());
        xcOrder.setCreateDate(LocalDateTime.now());
        xcOrder.setStatus("600001"); // 未支付
        xcOrder.setUserId(userId);
        xcOrder.setOrderType("60201"); // 订单类型
        xcOrder.setOrderName(addOrderDto.getOrderName());
        xcOrder.setOrderDescrip(addOrderDto.getOrderDescrip());
        xcOrder.setOrderDetail(addOrderDto.getOrderDetail());
        xcOrder.setOutBusinessId(addOrderDto.getOutBusinessId()); // 如果是选课这里记录课程表的id

        int insert = xcOrdersMapper.insert(xcOrder);
        if (insert <= 0) {
            XueChengPlusException.cast("添加订单失败");
        }
        // 订单id
        Long orderId = xcOrder.getId();

        // 插入订单明细表
        // 将前端传入的的明细json串转成List
        String orderDetailJson = addOrderDto.getOrderDetail();
        List<XcOrdersGoods> xcOrdersGoods = JSON.parseArray(orderDetailJson, XcOrdersGoods.class);
        // 遍历xcOrdersGoods插入订单明细表
        xcOrdersGoods.forEach(goods -> {
            goods.setOrderId(orderId);
            // 插入订单明细
            int insert1 = xcOrdersGoodsMapper.insert(goods);
        });

        return xcOrder;
    }

    /**
     * 根据业务id查询订单，业务id是选课记录表中的主键
     *
     * @param businessId
     * @return
     */
    public XcOrders getOrdersByBusinessId(String businessId) {
        return xcOrdersMapper.selectOne(new LambdaQueryWrapper<XcOrders>().eq(XcOrders::getOutBusinessId, businessId));
    }
}

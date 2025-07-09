package com.xuecheng.orders.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.IdWorkerUtils;
import com.xuecheng.base.utils.QRCodeUtil;
import com.xuecheng.orders.mapper.XcOrdersGoodsMapper;
import com.xuecheng.orders.mapper.XcOrdersMapper;
import com.xuecheng.orders.mapper.XcPayRecordMapper;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.po.XcOrders;
import com.xuecheng.orders.model.po.XcOrdersGoods;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private XcOrdersMapper xcOrdersMapper;

    @Autowired
    private XcOrdersGoodsMapper xcOrdersGoodsMapper;

    @Autowired
    private XcPayRecordMapper payRecordMapper;

    @Value("${pay.qrcodeurl}")
    String qrcodeurl;

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

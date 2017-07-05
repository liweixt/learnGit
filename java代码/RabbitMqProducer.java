package com.yunda.util;


import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;


public class Producer {
	private final static String QUEUE_NAME="callNum_queue";
	private final static String EXCHANGE_NAME="scan_filter_proto_callNum";
	private final static String EXCHANGE_TYPE="topic";
	private final static String HOST = "10.119.105.247";
	private final static String USER_NAME = "admin";
	private final static String PASSWORD = "admin";
	public static void main(String[] args) throws Exception {
		produc();
		//customer();
	}
	public static void produc() throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		
		factory.setHost(HOST);
		factory.setUsername(USER_NAME);
		factory.setPassword(PASSWORD);
//		factory.setPort(PORT);
		//创建一个新的连接
		Connection connection = factory.newConnection();
		
		//创建一个通道
		Channel channel = connection.createChannel();
		//声明要关注的队列
		channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE,true);
		
		//持久化  
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        //流量  
        channel.basicQos(1);  
        //将消息队列绑定到Exchange  
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, QUEUE_NAME);  
		
		
		//发送消息到队列中
		for(int i=0;i<10;i++){
			String message = "6800173819"+i;
			
			channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null, message.getBytes("UTF-8"));
			System.out.println("Producer Send +'" + message + "'");
		}
		
		//关闭通道和连接
		channel.close();
		connection.close();
		
	}
	public static void customer() throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		
		factory.setHost(HOST);
		factory.setUsername(USER_NAME);
		factory.setPassword(PASSWORD);
//		factory.setPort(PORT);
		//创建一个新的连接
        Connection connection = factory.newConnection();

        //创建一个通道
        Channel channel = connection.createChannel();
        //声明要关注的队列
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE,true);
        
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.basicQos(1);  
        //将消息队列绑定到Exchange  
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, QUEUE_NAME);  
        System.out.println("Customer Waiting Received messages");

        //创建队列消费者  
        QueueingConsumer consumer = new QueueingConsumer(channel); 
        //指定消费队列  
        channel.basicConsume(QUEUE_NAME, true, consumer); 
        while (true){  
            //nextDelivery是一个阻塞方法（内部实现其实是阻塞队列的take方法）  
            QueueingConsumer.Delivery delivery = consumer.nextDelivery(); 
            String message = new String(delivery.getBody(),"UTF-8"); 
            System.out.println("Received '" + message + "'");
        }  
 
	}
}

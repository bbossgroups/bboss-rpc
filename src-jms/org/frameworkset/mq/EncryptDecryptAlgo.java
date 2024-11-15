package org.frameworkset.mq;


/**
 * 
 * <p>Title: EncryptDecryptAlgo.java</p>
 * <p>Description:简单的加密和解密算法 </p>
 * <p>Copyright: Copyright (c) 2009</p>
 * <p>Company: </p>
 * @Date May 17, 2009
 * @author
 * @version 1.0
 */
public class EncryptDecryptAlgo
{	// Encrypting a string value.
	/**
	 * 字符串加密
	 * @param plainText
	 * @return
	 */
	public String encrypt(String plainText) 
	{    org.frameworkset.coder.BASE64Encoder BASE64Encoder = new org.frameworkset.coder.BASE64Encoder();
	     String encode = BASE64Encoder.encodeBuffer(plainText.getBytes());
	     return encode;

  	}
	/**
	 * 字节数组加密
	 * @param in
	 * @return
	 */
	public byte[] encrypt(byte[] in) 
	{    org.frameworkset.coder.BASE64Encoder BASE64Encoder = new org.frameworkset.coder.BASE64Encoder();
	     String encode = BASE64Encoder.encodeBuffer(in);
	     return encode.getBytes();

  	}
	
	/**
	 * 字符串解密
	 * @param plainText
	 * @return
	 */
	public String decrypt(String plainText) 
	{
	try {
        org.frameworkset.coder.BASE64Decoder BASE64Decoder= new  org.frameworkset.coder.BASE64Decoder();
		 String decode=new String(BASE64Decoder.decodeBuffer(plainText));
		 return decode;
	} catch (Exception e) {
		return null;
	}
	}
	/**
	 * 字节数组解密
	 * @param in
	 * @return
	 */
	public byte[] decrypt(byte[] in) 
	{
	try {
        org.frameworkset.coder.BASE64Decoder BASE64Decoder= new  org.frameworkset.coder.BASE64Decoder();
		 byte[] decode=BASE64Decoder.decodeBuffer(new String(in));
		 return decode;
	} catch (Exception e) {
		return null;
	}
	
  	}
	

}


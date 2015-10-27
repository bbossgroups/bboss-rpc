/*
 *  Copyright 2008 biaoping.yin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.frameworkset.spi.remote.serializable;

import java.io.ByteArrayInputStream;

import com.caucho.hessian.io.HessianInput;

/**
 * <p>Title: HessianDecoder.java</p> 
 * <p>Description: </p>
 * <p>bboss workgroup</p>
 * <p>Copyright (c) 2007</p>
 * @Date 2012-1-30 下午08:36:27
 * @author biaoping.yin
 * @version 1.0
 */
public class HessianDecoder   implements Decoder{

	public Object decoder(Object msg) throws Exception {
		if(msg == null)
			return null;
		if(msg instanceof byte[]){
			ByteArrayInputStream is = new ByteArrayInputStream((byte[])msg);   
			 HessianInput hi = new HessianInput(is);   
			 return hi.readObject();   
		}
		else
			return msg;
	}

}

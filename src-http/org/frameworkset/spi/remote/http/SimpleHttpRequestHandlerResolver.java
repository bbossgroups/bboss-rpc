/*
 * $HeadURL: https://svn.apache.org/repos/asf/httpcomponents/httpcore/tags/4.0.1/httpcore-nio/src/test/java/org/apache/http/mockup/SimpleHttpRequestHandlerResolver.java $
 * $Revision: 575207 $
 * $Date: 2007-09-13 09:57:05 +0200 (Thu, 13 Sep 2007) $
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.frameworkset.spi.remote.http;

import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerResolver;

public class SimpleHttpRequestHandlerResolver implements HttpRequestHandlerResolver {

    private final HttpRequestHandler handler;
    
    public SimpleHttpRequestHandlerResolver(final HttpRequestHandler handler) {
        super();
        this.handler = handler;
    }
    
    public HttpRequestHandler lookup(final String requestURI) {
        return this.handler;
    }

}

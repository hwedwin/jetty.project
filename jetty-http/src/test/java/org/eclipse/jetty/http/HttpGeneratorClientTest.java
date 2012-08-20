//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.matchers.JUnitMatchers.containsString;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.junit.Test;

public class HttpGeneratorClientTest
{
    public final static String CONTENT="The quick brown fox jumped over the lazy dog.\nNow is the time for all good men to come to the aid of the party\nThe moon is blue to a fish in love.\n";
    public final static String[] connect={null,"keep-alive","close"};

    class Info extends HttpGenerator.RequestInfo
    {
        Info(String method,String uri)
        {
            super(HttpVersion.HTTP_1_1,new HttpFields(),-1,method,uri);
        }

        public Info(String method,String uri, int contentLength)
        {
            super(HttpVersion.HTTP_1_1,new HttpFields(),contentLength,method,uri);
        }
    }

    @Test
    public void testRequestNoContent() throws Exception
    {
        ByteBuffer header=BufferUtil.allocate(2048);
        HttpGenerator gen = new HttpGenerator();

        HttpGenerator.Result
        result=gen.generateRequest(null,null,null,true);
        assertEquals(HttpGenerator.Result.NEED_INFO,result);
        assertEquals(HttpGenerator.State.START,gen.getState());
        
        Info info = new Info("GET","/index.html");
        info.getHttpFields().add("Host","something");
        info.getHttpFields().add("User-Agent","test");
        assertTrue(!gen.isChunking());

        result=gen.generateRequest(info,null,null,true);
        assertEquals(HttpGenerator.Result.NEED_HEADER,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        result=gen.generateRequest(info,header,null,true);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMPLETING,gen.getState());
        assertTrue(!gen.isChunking());
        String out = BufferUtil.toString(header);
        BufferUtil.clear(header);

        result=gen.generateResponse(null,null,null,false);
        assertEquals(HttpGenerator.Result.DONE,result);
        assertEquals(HttpGenerator.State.END,gen.getState());
        assertTrue(!gen.isChunking());

        assertEquals(0,gen.getContentPrepared());
        assertThat(out,containsString("GET /index.html HTTP/1.1"));
        assertThat(out,not(containsString("Content-Length")));

    }

    @Test
    public void testRequestWithContent() throws Exception
    {
        String out;
        ByteBuffer header=BufferUtil.allocate(4096);
        ByteBuffer content0=BufferUtil.toBuffer("Hello World. The quick brown fox jumped over the lazy dog.");
        HttpGenerator gen = new HttpGenerator();

        HttpGenerator.Result
        result=gen.generateRequest(null,null,content0,true);
        assertEquals(HttpGenerator.Result.NEED_INFO,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        Info info = new Info("POST","/index.html");
        info.getHttpFields().add("Host","something");
        info.getHttpFields().add("User-Agent","test");

        result=gen.generateRequest(info,null,content0,true);
        assertEquals(HttpGenerator.Result.NEED_HEADER,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        result=gen.generateRequest(info,header,content0,true);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMPLETING,gen.getState());
        assertTrue(!gen.isChunking());
        out = BufferUtil.toString(header);
        BufferUtil.clear(header);
        out+=BufferUtil.toString(content0);
        BufferUtil.clear(content0);

        result=gen.generateResponse(null,null,null,false);
        assertEquals(HttpGenerator.Result.DONE,result);
        assertEquals(HttpGenerator.State.END,gen.getState());
        assertTrue(!gen.isChunking());
        
        
        assertThat(out,containsString("POST /index.html HTTP/1.1"));
        assertThat(out,containsString("Host: something"));
        assertThat(out,containsString("Content-Length: 58"));
        assertThat(out,containsString("Hello World. The quick brown fox jumped over the lazy dog."));

        assertEquals(58,gen.getContentPrepared());
    }

    @Test
    public void testRequestWithChunkedContent() throws Exception
    {
        String out;
        ByteBuffer header=BufferUtil.allocate(4096);
        ByteBuffer chunk=BufferUtil.allocate(HttpGenerator.CHUNK_SIZE);
        ByteBuffer content0=BufferUtil.toBuffer("Hello World. ");
        ByteBuffer content1=BufferUtil.toBuffer("The quick brown fox jumped over the lazy dog.");
        HttpGenerator gen = new HttpGenerator();

        HttpGenerator.Result
        result=gen.generateRequest(null,null,content0,false);
        assertEquals(HttpGenerator.Result.NEED_INFO,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        Info info = new Info("POST","/index.html");
        info.getHttpFields().add("Host","something");
        info.getHttpFields().add("User-Agent","test");

        result=gen.generateRequest(info,null,content0,false);
        assertEquals(HttpGenerator.Result.NEED_HEADER,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        result=gen.generateRequest(info,header,content0,false);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMMITTED,gen.getState());
        assertTrue(gen.isChunking());
        out = BufferUtil.toString(header);
        BufferUtil.clear(header);
        out+=BufferUtil.toString(content0);
        BufferUtil.clear(content0);

        result=gen.generateRequest(null,header,content1,false);
        assertEquals(HttpGenerator.Result.NEED_CHUNK,result);
        assertEquals(HttpGenerator.State.COMMITTED,gen.getState());

        result=gen.generateRequest(null,chunk,content1,false);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMMITTED,gen.getState());
        assertTrue(gen.isChunking());
        out+=BufferUtil.toString(chunk);
        BufferUtil.clear(chunk);
        out+=BufferUtil.toString(content1);
        BufferUtil.clear(content1);
        
        result=gen.generateResponse(null,chunk,null,true);
        assertEquals(HttpGenerator.Result.CONTINUE,result);
        assertEquals(HttpGenerator.State.COMPLETING,gen.getState());
        assertTrue(gen.isChunking());
        
        result=gen.generateResponse(null,chunk,null,true);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMPLETING,gen.getState());
        out+=BufferUtil.toString(chunk);
        BufferUtil.clear(chunk);
        assertTrue(!gen.isChunking());

        result=gen.generateResponse(null,chunk,null,true);
        assertEquals(HttpGenerator.Result.DONE,result);
        assertEquals(HttpGenerator.State.END,gen.getState());
        
        assertThat(out,containsString("POST /index.html HTTP/1.1"));
        assertThat(out,containsString("Host: something"));
        assertThat(out,containsString("Transfer-Encoding: chunked"));
        assertThat(out,containsString("\r\nD\r\nHello World. \r\n"));
        assertThat(out,containsString("\r\n2D\r\nThe quick brown fox jumped over the lazy dog.\r\n"));
        assertThat(out,containsString("\r\n0\r\n\r\n"));

        assertEquals(58,gen.getContentPrepared());
        
    }
    
    @Test
    public void testRequestWithKnownContent() throws Exception
    {
        String out;
        ByteBuffer header=BufferUtil.allocate(4096);
        ByteBuffer chunk=BufferUtil.allocate(HttpGenerator.CHUNK_SIZE);
        ByteBuffer content0=BufferUtil.toBuffer("Hello World. ");
        ByteBuffer content1=BufferUtil.toBuffer("The quick brown fox jumped over the lazy dog.");
        HttpGenerator gen = new HttpGenerator();

        HttpGenerator.Result
        result=gen.generateRequest(null,null,content0,false);
        assertEquals(HttpGenerator.Result.NEED_INFO,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        Info info = new Info("POST","/index.html",58);
        info.getHttpFields().add("Host","something");
        info.getHttpFields().add("User-Agent","test");

        result=gen.generateRequest(info,null,content0,false);
        assertEquals(HttpGenerator.Result.NEED_HEADER,result);
        assertEquals(HttpGenerator.State.START,gen.getState());

        result=gen.generateRequest(info,header,content0,false);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMMITTED,gen.getState());
        assertTrue(!gen.isChunking());
        out = BufferUtil.toString(header);
        BufferUtil.clear(header);
        out+=BufferUtil.toString(content0);
        BufferUtil.clear(content0);

        result=gen.generateRequest(null,null,content1,false);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        assertEquals(HttpGenerator.State.COMMITTED,gen.getState());
        assertTrue(!gen.isChunking());
        out+=BufferUtil.toString(content1);
        BufferUtil.clear(content1);
        
        result=gen.generateResponse(null,null,null,true);
        assertEquals(HttpGenerator.Result.CONTINUE,result);
        assertEquals(HttpGenerator.State.COMPLETING,gen.getState());
        assertTrue(!gen.isChunking());
        
        result=gen.generateResponse(null,null,null,true);
        assertEquals(HttpGenerator.Result.DONE,result);
        assertEquals(HttpGenerator.State.END,gen.getState());
        out+=BufferUtil.toString(chunk);
        BufferUtil.clear(chunk);
        
        assertThat(out,containsString("POST /index.html HTTP/1.1"));
        assertThat(out,containsString("Host: something"));
        assertThat(out,containsString("Content-Length: 58"));
        assertThat(out,containsString("\r\n\r\nHello World. The quick brown fox jumped over the lazy dog."));

        assertEquals(58,gen.getContentPrepared());
        
    }

}

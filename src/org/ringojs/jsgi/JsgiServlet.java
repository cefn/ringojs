/*
 *  Copyright 2009 Hannes Wallnoefer <hannes@helma.at>
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

package org.ringojs.jsgi;

import org.eclipse.jetty.continuation.ContinuationSupport;
import org.mozilla.javascript.Context;
import org.ringojs.tools.RingoConfiguration;
import org.ringojs.tools.RingoRunner;
import org.ringojs.repository.Repository;
import org.ringojs.repository.FileRepository;
import org.ringojs.repository.WebappRepository;
import org.ringojs.engine.RhinoEngine;
import org.ringojs.util.StringUtils;
import org.mozilla.javascript.Callable;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.File;

public class JsgiServlet extends HttpServlet {

    String module;
    Object function;
    RhinoEngine engine;
    JsgiEnv envproto;

    public JsgiServlet() {}

    public JsgiServlet(RhinoEngine engine) throws ServletException {
        this(engine, null);
    }

    public JsgiServlet(RhinoEngine engine, Callable callable) throws ServletException {
        this.engine = engine;
        this.function = callable;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // don't overwrite function if it was set in constructor
        if (function == null) {
            module = getInitParam(config, "moduleName", "config");
            function = getInitParam(config, "functionName", "app");
        }

        if (engine == null) {
            String ringoHome = getInitParam(config, "ringoHome", "WEB-INF");
            String modulePath = getInitParam(config, "modulePath", "app");

            Repository home = new WebappRepository(config.getServletContext(), ringoHome);
            try {
                if (!home.exists()) {
                    home = new FileRepository(ringoHome);
                }
                String[] paths = StringUtils.split(modulePath, File.pathSeparator);
                RingoConfiguration ringoConfig = new RingoConfiguration(home, paths, "modules");
                engine = new RhinoEngine(ringoConfig, null);
            } catch (Exception x) {
                throw new ServletException(x);
            }
        }

        Context cx = engine.getContextFactory().enterContext();
        try {
            envproto = new JsgiEnv(cx, engine.getScope());
        } catch (NoSuchMethodException nsm) {
            throw new ServletException(nsm);
        } finally {
            Context.exit();
        }

    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            if (ContinuationSupport.getContinuation(request).isExpired()) {
                // Continuation timeouts are handled by ringo/jsgi module
                return;
            }
            JsgiEnv env = new JsgiEnv(request, response, envproto, engine.getScope());
            engine.invoke("ringo/jsgi", "handleRequest", module, function, env);
        } catch (NoSuchMethodException x) {
            RingoRunner.reportError(x, System.err, false);
            throw new ServletException(x);
        } catch (Exception x) {
            RingoRunner.reportError(x, System.err, false);
            throw new ServletException(x);
        }
    }

    private String getInitParam(ServletConfig config, String name, String defaultValue) {
        String value = config.getInitParameter(name);
        return value == null ? defaultValue : value;
    }

}

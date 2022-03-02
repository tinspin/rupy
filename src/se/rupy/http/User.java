package se.rupy.http;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONObject;

import se.rupy.http.*;

/* Making a generic user HTML login/registration system 
 * with async-to-async is very complex. There are two ways to go:
 * 1) require AJAX javascript modules (not great for all use cases).
 * 2) require extremely complex redirect loops, async pushes and user flows.
 * Right now we opt for no. 2 with simplified requirements that you
 * include the /user "module" on the root path (/), 
 * we will make an example project soon.
 */
public class User extends Service {
	private String host(Event event) throws Exception {
		return event.query().string("root", Root.host());
	}
	
	private String head(Event event) throws Exception {
		return "Head:less\r\nHost:" + event.query().string("root", host(event));
	}

	public static void redirect(Event event) throws IOException, Event {
		String referer = event.query().header("referer");
		redirect(event, referer == null ? "/" : referer, true);
	}

	public static void redirect(Event event, String path) throws IOException, Event {
		redirect(event, path, false);
	}

	public static void redirect(Event event, String path, boolean forward) throws IOException, Event {
		if(forward) {
			HashMap query = (HashMap) event.query().clone();
			event.session().put("post", query);
		}

		event.reply().header("Location", path);
		event.reply().code("302 Found");
		Output out = event.output();
		out.finish();
		out.flush();
		throw event;
	}

	public static void refill(Event event) {
		HashMap post = (HashMap) event.session().get("post");

		if(post != null) {
			Iterator it = post.keySet().iterator();

			while(it.hasNext()) {
				Object key = it.next();
				Object value = post.get(key);

				event.query().put(key, value);
			}
		}
	}

	public String path() { return "/user"; }

	private void script(Event event) throws Exception {
		Output out = event.output();
		String salt = event.session().string("salt");
		String algo = event.query().string("algo", "sha-256");

		if(algo.equals("sha-256"))
			out.println("<script src=\"sha256.js\"></script>");
		else
			out.println("<script src=\"md5.js\"></script>");
		out.println("<script>");
		out.println("  var word = '';");
		out.println("  function join(e) {");
		out.println("    e = e || window.event;");
		out.println("    var unicode = e.charCode ? e.charCode : e.keyCode ? e.keyCode : 0;");
		out.println("    if(unicode == 13) {");
		out.println("      hash('join');");
		out.println("    }");
		out.println("  }");
		out.println("  function sign(e) {");
		out.println("    e = e || window.event;");
		out.println("    var unicode = e.charCode ? e.charCode : e.keyCode ? e.keyCode : 0;");
		out.println("    var s = String.fromCharCode(e.target.value.charAt(e.target.selectionStart - 1).charCodeAt());");
		out.println("    if(navigator.userAgent.indexOf('Android') == -1) {");
        out.println("      s = String.fromCharCode(unicode);");
        out.println("      if(!e.shiftKey)");
        out.println("        s = s.toLowerCase();");
        out.println("    }");
		out.println("    if(unicode == 13) {");
		out.println("      hash('sign');");
		out.println("    }");
		out.println("    else if(unicode == 8) {");
		out.println("      word = word.substring(0, word.length - 1);");
		out.println("    }");
		out.println("    else if(unicode > 31) {");
		out.println("      word += s;");
		out.println("      var hide = document.getElementById('hide');");
		out.println("      hide.value = '';");
		out.println("      for(var i = 0; i < word.length; i++) {");
		out.println("        hide.value += '•';");
		out.println("      }");
		out.println("      return false;");
		out.println("    }");
		out.println("  }");
		out.println("  var digits = /^\\d+$/;");
		out.println("  function hash(type) {");
		out.println("    var name = document.getElementById('name');");
		out.println("    var hide = document.getElementById('hide');");
		out.println("    var salt = document.getElementById('salt');");
		out.println("    if(word.length > 0) {");
		out.println("      if(type == 'join') {");
		if(algo.equals("sha-256"))
			out.println("        pass.value = CryptoJS.SHA256(word + name.value.toLowerCase());");
		else
			out.println("        pass.value = md5(word + name.value.toLowerCase());");
		out.println("      } else {");
		out.println("        salt.value = '" + salt + "';");
		out.println("        var id_login = digits.test(name.value);");
		out.println("        if(!id_login)");
		if(algo.equals("sha-256")) {
			out.println("          pass.value = CryptoJS.SHA256(word + name.value.toLowerCase());");
			out.println("        pass.value = CryptoJS.SHA256(id_login ? word : pass.value + salt.value);");
		}
		else {
			out.println("          pass.value = md5(word + name.value.toLowerCase());");
			out.println("        pass.value = md5(id_login ? word : pass.value + salt.value);");
		}
		out.println("      }");
		out.println("      document.forms['user'].submit();");
		out.println("    }");
		out.println("  }");
		out.println("</script>");
		out.println("<style>");
		out.println("  a:link, a:hover, a:active, a:visited { color: #6699ff; font-style: italic; }");
		out.println("  div { font-family: monospace; }");
		out.println("  input { font-family: monospace; }");
		out.println("</style>");
	}

	private void print(Event event, String feedback) throws Event, Exception {
		Output out = event.output();
		String name = event.string("name");
		String mail = event.string("mail");
		String salt = event.string("salt");
		String fail = event.string("fail");
		String bare = event.query().string("bare", "");
		String host = event.query().header("host");
		String url = event.query().string("url", host);

		if(bare.length() == 0) {
			out.println("<!doctype html>");
			out.println("<html>");
			out.println("<head>");
			out.println("<meta charset=\"utf-8\">");
			out.println("<meta name=\"viewport\" content=\"width=300, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">");
		}

		script(event);
		
		if(bare.length() == 0) {
			out.println("</head>");
			out.println("<body>");
		}

		out.println("<div><table width=\"100\">");

		if(fail != null) {
			out.println("<tr><td colspan=\"2\"><i><font color=\"#ff3300\">" + fail + "</font></i></td></tr>");
		}

		// mail is not compatible with distributed database on user creation.
		// if the "insert" fails, nodes will have the mail and successive registers
		// will fail. add mail when user has been successfully registered for
		// password reset and account recovery.
		
		out.println("<tr>");
		out.println("<form action=\"user\" method=\"post\" name=\"user\"><input type=\"hidden\" name=\"salt\" id=\"salt\" value=\"" + salt + "\"><input type=\"hidden\" name=\"pass\" id=\"pass\" value=\"\"><input type=\"hidden\" name=\"url\" value=\"" + url + "\">");
		out.println("<td><font color=\"#00cc33\"><i>name</i></font>&nbsp;</td><td><input type=\"text\" style=\"width: 100px;\" name=\"name\" id=\"name\" value=\"" + name + "\"></td></tr>");
		out.println("<tr><td><font color=\"#00cc33\"><i>pass</i></font>&nbsp;</td><td><input type=\"text\" style=\"width: 100px;\" name=\"hide\" id=\"hide\" onkeyup=\"return sign(event);\"></td></tr>");
		//out.println("<tr><td><font color=\"#00cc33\"><i>mail*</i></font></td><td><input type=\"text\" style=\"width: 100px;\" name=\"mail\" value=\"" + mail + "\" onkeypress=\"join(event);\"></td></tr>");
		out.println("<tr><td></td><td><a href=\"javascript:hash('sign');\">login</a>&nbsp;<a href=\"javascript:hash('join');\"><font color=\"#ff9900\">register</font></a></td></tr>");
		//out.println("<tr><td></td><td><font color=\"#ff9900\"><i>*optional</i></font></td></tr>");
		out.println("</form>");
		out.println("</table></div>");

		if(bare.length() == 0) {
			out.println("</body>");
			out.println("</html>");
		}
	}

	public void filter(Event event) throws Event, Exception {
		event.query().parse();

		String algo = event.query().string("algo", "sha-256");

		if(event.push()) {
			String name = event.query().string("success");
			String fail = event.query().string("fail");
			String host = event.query().header("host");
			String url = event.query().string("url", host);

			JSONObject user = (JSONObject) event.query().get("user");
			
			if(user != null) {
				String pass = event.string("pass");
				String salt = event.string("salt");
				String hash = Deploy.hash(Deploy.hash(user.getString("pass") + user.getString("name"), algo) + salt, algo);

				if(hash.equals(pass)) {
					user.remove("pass");
					event.output().print(user);
				}
				else {
					event.query().put("fail", "pass didn't match");
					redirect(event);
				}
			}
			else if(name.length() > 0) {
				redirect(event, "http://" + url + "?name=" + name);
			}
			else if(fail.length() > 0) {
				Output out = event.output();
				out.println("<meta http-equiv=\"refresh\" content=\"0;URL=http://" + url + "?fail=" + URLEncoder.encode(fail, "UTF-8") + "\">");
				out.finish();
				out.flush();
				throw event;
			}
			else if(event.bit("redirect")) {
				Output out = event.output();
				out.println("<meta http-equiv=\"refresh\" content=\"0;URL=http://" + url + "?name=" + name + "\">");
				out.finish();
				out.flush();
				throw event;
			}
		}
		else {
			if(event.query().method() == Query.GET) {
				refill(event);
				
				String salt = event.session().string("salt");

				if(salt.length() == 0) {
					event.hold();
					
					Async.Work work = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.get("/salt", head(event));
						}

						public void read(String host, String body) throws Exception {
							event.query().put("redirect", "true");
							event.session().put("salt", body);
							event.reply().wakeup(true);
						}

						public void fail(String host, Exception e) throws Exception {
							e.printStackTrace();
							event.reply().wakeup(true);
						}
					};

					event.daemon().client().send("localhost", work, 30);
					throw event;
				}
				else {
					Output out = event.output();
					print(event, null);
					out.finish();
					out.flush();
				}
			}
			else if(event.query().method() == Query.POST) {
				final String name = event.string("name").toLowerCase();
				final String salt = event.string("salt");
				final String pass = event.string("pass");
				String host = event.string("host");

				if(name.length() < 2) {
					event.query().put("fail", "name too short (2)");
					redirect(event);
				}

				if(salt.length() > 0) {
					if(event.session() != null)
						event.session().put("salt", null);
					
					if(host.equals(Root.host())) {
						if(Root.Salt.salt.containsKey(salt)) {
							Root.Salt.salt.remove(salt);
						}
						else {
							event.reply().code("400 Bad Request");
							event.output().print("salt not found");
							throw event;
						}
						
						File file = null;
						
						if(name.indexOf("@") > -1) { // this doesen't work because name is used as salt!
							file = new File(Root.home() + "/node/user/mail" + Root.path(name));
						}
						else if(name.matches("[0-9]+")) {
							file = new File(Root.home() + "/node/user/id" + Root.path(Long.parseLong(name)));
						}
						else {
							file = new File(Root.home() + "/node/user/name" + Root.path(name));
						}
						
						if(!file.exists()) {
							event.output().print("name not found");
							throw event;
						}
						
						JSONObject object = new JSONObject(Root.file(file));
						
						String secret = object.optString("pass");
						boolean key = false;
						
						if(secret.length() == 0) {
							secret = object.optString("key");
							key = true;
						}
						
						String hash = Deploy.hash(secret + salt, algo);
						
						if(hash.equals(pass)) {
							object.remove("pass");
							event.output().print(object);
						}
						else if(key == false) {
							secret = object.optString("key");
							hash = Deploy.hash(secret + salt, algo);
							
							if(hash.equals(pass)) {
								object.remove("pass");
								event.output().print(object);
							}
							else {
								event.output().print("wrong pass");
							}
						}
						else {
							event.output().print("wrong pass");
						}

						throw event;
					}
					else {
						Async.Work work = new Async.Work(event) {
							public void send(Async.Call call) throws Exception {
								String body = "name=" + name + "&pass=" + pass + "&salt=" + salt + "&host=" + host(event);
								call.post("/user", head(event), body.getBytes());
							}

							public void read(String host, String body) throws Exception {
								try {
									JSONObject user = new JSONObject(body);
									event.session().put("user", user);
									event.query().put("success", user.getString("name"));
								}
								catch(Exception e) {
									event.query().put("fail", body);
								}

								event.reply().wakeup(true);
							}

							public void fail(String host, Exception e) throws Exception {
								e.printStackTrace();
								event.query().put("fail", "something snapped");
								event.reply().wakeup(true);
							}
						};

						event.daemon().client().send("localhost", work, 30);
						throw event;
					}
				}
				else {
					String mail = event.string("mail").toLowerCase();

					/* The distributed name service I'm building
				     * is going to use 5-bit letters so to fit inside
				     * an integer we can only have 6 letters.
				     * o = 0
				     * i = 1
				     * x and z removed
				     * oi23456789abcdefghjklmnpqrstuvwy
				     */
					if(name.length() > 6) {
						event.query().put("fail", "name too long (6)");
						redirect(event);
					}

					if(!name.matches("[a-wyA-WY2-9]+")) {
					    event.query().put("fail", "name invalid (a-wy/2-9)");
					    redirect(event);
                    }

				    if(name.matches("[0-9]+")) {
					    event.query().put("fail", "name alpha missing"); // [0-9]+ reserved for <id>
					    redirect(event);
                    }

					if(mail.length() > 0 && mail.indexOf("@") == -1) {
						event.query().put("fail", "mail @ missing");
						redirect(event);
					}

					String user = "{\"name\":\"" + name + "\",\"pass\":\"" + pass + "\"";
					String list = "key,name";

					if(mail.length() > 0) {
						user += ",\"mail\":\"" + mail + "\"";
						list += ",mail";
					}

					user += "}";

					final String json = user;
					final String sort = list;

					Async.Work work = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.post("/node", head(event), ("json=" + json + "&sort=" + sort + "&create").getBytes("utf-8"));
						}

						public void read(String host, String body) throws Exception {
							boolean invalid = body.indexOf("Validation") > 0;
							boolean collide = body.indexOf("Collision") > 0;

							if(invalid || collide) {
								String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));
								event.query().put("fail", message.substring(0, 4) + " " + 
										(invalid ? "contains bad characters" : "") + " " + 
										(collide ? "already registered" : ""));
							}
							else {
								JSONObject user = new JSONObject(body);
								event.session().put("user", user);
								event.query().put("success", user.getString("name"));
							}

							event.reply().wakeup(true);
						}

						public void fail(String host, Exception e) throws Exception {
							e.printStackTrace();
							event.query().put("fail", e.toString() + "[" + Root.local() + "]");
							event.reply().wakeup(true);
						}
					};

					event.daemon().client().send("localhost", work, 30);
					throw event;
				}
			}
		}
	}
}
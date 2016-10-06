package org.myrobotlab.framework;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.service.Runtime;
import org.myrobotlab.service.interfaces.ServiceInterface;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * handles all encoding and decoding of MRL messages or api(s) assumed context -
 * services can add an assumed context as a prefix
 * /api/returnEncoding/inputEncoding/service/method/param1/param2/ ...
 * 
 * xmpp for example assumes (/api/string/gson)/service/method/param1/param2/ ...
 * 
 * scheme = alpha *( alpha | digit | "+" | "-" | "." ) Components of all URIs:
 * [<scheme>:]<scheme-specific-part>[#<fragment>]
 * http://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
 * 
 * branch API test 5
 */
public class Encoder {

	public final static Logger log = LoggerFactory.getLogger(Encoder.class);

	// uri schemes
	public final static String SCHEME_MRL = "mrl";

	public final static String SCHEME_BASE64 = "base64";

	public final static String TYPE_JSON = "json";

	public final static String TYPE_REST = "rest";

	// disableHtmlEscaping to prevent encoding or "=" -
	// private transient static Gson gson = new
	// GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").setPrettyPrinting().disableHtmlEscaping().create();
	private transient static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").disableHtmlEscaping().create();
	// FIXME - switch to Jackson
	
	public final static String PREFIX_API = "api";

	// public final static String PREFIX_API = "/api/services";
	// public final static String PREFIX_API = "/api/services";
	// public final static String PREFIX_REST_API = "services";
	
	

	public static final Set<Class<?>> WRAPPER_TYPES = new HashSet<Class<?>>(Arrays.asList(Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class,
			Float.class, Double.class, Void.class));

	public static final Set<String> WRAPPER_TYPES_CANONICAL = new HashSet<String>(Arrays.asList(Boolean.class.getCanonicalName(), Character.class.getCanonicalName(),
			Byte.class.getCanonicalName(), Short.class.getCanonicalName(), Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Float.class.getCanonicalName(),
			Double.class.getCanonicalName(), Void.class.getCanonicalName()));

	final static HashMap<String, Method> methodCache = new HashMap<String, Method>();

	/**
	 * a method signature map based on name and number of methods - the String[]
	 * will be the keys into the methodCache A method key is generated by input
	 * from some encoded protocol - the method key is object name + method name
	 * + parameter number - this returns a full method signature key which is
	 * used to look up the method in the methodCache
	 */
	final static HashMap<String, ArrayList<Method>> methodOrdinal = new HashMap<String, ArrayList<Method>>();

	final static HashSet<String> objectsCached = new HashSet<String>();

	public static final Message base64ToMsg(String base64) {
		String data = base64;
		if (base64.startsWith(String.format("%s://", SCHEME_BASE64))) {
			data = base64.substring(SCHEME_BASE64.length() + 3);
		}
		final ByteArrayInputStream dataStream = new ByteArrayInputStream(Base64.decodeBase64(data));
		try {
			final ObjectInputStream objectStream = new ObjectInputStream(dataStream);
			Message msg = (Message) objectStream.readObject();
			return msg;
		} catch (Exception e) {
			Logging.logError(e);
			return null;
		}
	}


	// FIXME - need to throw on error - returning null is "often" valid
	public static Object invoke(String uri) throws IOException {
		Message msg = Encoder.decodePathInfo(uri);
		ServiceInterface si = Runtime.getService(msg.name);
		Object ret = si.invoke(msg.method, msg.data);
		return ret;
	}
	
	
	/**
	 * FIXME - this method requires the class to be loaded for type conversions !!!
	 * Decoding a URI or path can depend on Context & Environment 
	 * part of decoding relies on the method signature of an object - therefore it has to be loaded in memory, but
	 * if the ability to send messages from outside this system is desired - then the Message must be
	 * able to SPECIFY THE DECODING IT NEEDS !!! - without the clazz available !!!
	 * 
	 * URI path decoder - decodes a path into a MRL Message.
	 * Details are here http://myrobotlab.org/content/myrobotlab-api
	 * JSON is the default encoding
	 * 
	 * @param pathInfo - input path in the format - /{api-type}(/encoding=json/decoding=json/)/{method}/{param0}/{param1}/...
	 * @return
	 * @throws IOException
	 */
	public static final Message decodePathInfo(String pathInfo) throws IOException {

		// FIXME optimization of HashSet combinations of supported encoding instead
		// of parsing...
		// e.g. HashMap<String> supportedEncoding.containsKey(
		// refer to - http://myrobotlab.org/content/myrobotlab-api
	
		String[] parts = pathInfo.split("/");
		String trailingCharacter = pathInfo.substring(pathInfo.length() - 1); 
		
		// synchronous - blocking 
		// Encoder.invoke(Outputs = null, "path");
		// search for //: for protocol ?
		
		// api has functionality .. 
		// it delivers the next "set" of access points - which is the services
		// this allows the calling interface to query
		
		if (!PREFIX_API.equals(parts[1])){
			throw new IOException(String.format("/api expected received %s", pathInfo));
		}
		
		// base query /api
		// this resolves to a "System" level call
		// at this point the caller does not need to know the Service names
		// have 2 choices /api & /api/
		/* FIXME !!!
		 * ONE VERY LARGE PROBLEM IS THERE IS NO DEFINITION for nameless Messages NOR 2 api states
		if (parts.length == 2 && "/".equals(trailingCharacter)){
			Message msg = new Message();
			String services = Encoder.toJson(Runtime.getServices());
			out.write(services.getBytes());
			out.flush();
			// close ?
			return;
			
		} else if  ("/api/services/".equals(pathInfo)){
			Encoder.write(out, Runtime.getServiceNames());
			out.flush();
			return;
		
		
		FIXME - not true - need to generate method for /api & /api/
		if (parts.length < 4) {
			throw new IOException(String.format("%s - not enough parts - requires minimal 4", pathInfo));
		}
		*/

		if (!PREFIX_API.equals(parts[1])) {
			log.error(String.format("apiTag %s specified but %s in ordinal", PREFIX_API, parts[0]));
			return null;
		}

		// FIXME INVOKING VS PUTTING A MESSAGE ON THE BUS
		Message msg = new Message();
		msg.name = parts[2];
		msg.method = parts[3];

		if (parts.length > 4) {
			// FIXME - ALL STRINGS AT THE MOMENT !!!
			String[] jsonParams = new String[parts.length - 4];
			System.arraycopy(parts, 4, jsonParams, 0, parts.length - 4);
			ServiceInterface si = org.myrobotlab.service.Runtime.getService(msg.name);
			// FIXME - this is a huge assumption ! - needs to be dynamic !
			msg.data = TypeConverter.getTypedParamsFromJson(si.getClass(), msg.method, jsonParams);
		}

		return msg;
	}

	public static Message decodeURI(URI uri) throws IOException {
		log.info(String.format("authority %s", uri.getAuthority())); // gperry:blahblah@localhost:7777
		log.info(String.format("     host %s", uri.getHost())); // localhost
		log.info(String.format("     port %d", uri.getPort())); // 7777
		log.info(String.format("     path %s", uri.getPath()));
		log.info(String.format("    query %s", uri.getQuery())); // /api/string/gson/runtime/getUptime
		log.info(String.format("   scheme %s", uri.getScheme())); // http
		log.info(String.format(" userInfo %s", uri.getUserInfo())); // gperry:blahblah

		Message msg = decodePathInfo(uri.getPath());

		return msg;
	}

	public final static <T extends Object> T fromJson(String json, Class<T> clazz) {
		return gson.fromJson(json, clazz);
	}

	static public final byte[] getBytes(Object o) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(5000);
		ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		os.flush();
		os.writeObject(o);
		os.flush();
		return byteStream.toByteArray();
	}

	static public final String getCallBack(String methodName) {
		String callback = String.format("on%s%s", methodName.substring(0, 1).toUpperCase(), methodName.substring(1));
		return callback;
	}

	// concentrator data coming from decoder
	static public Method getMethod(String serviceType, String methodName, Object[] params) {
		return getMethod("org.myrobotlab.service", serviceType, methodName, params);
	}

	// real encoded data ??? getMethodFromXML getMethodFromJson - all resolve to
	// this getMethod with class form
	// encoded data.. YA !
	static public Method getMethod(String pkgName, String objectName, String methodName, Object[] params) {
		String fullObjectName = String.format("%s.%s", pkgName, objectName);
		return null;
	}

	static public ArrayList<Method> getMethodCandidates(String serviceType, String methodName, int paramCount) {
		if (!objectsCached.contains(serviceType)) {
			loadObjectCache(serviceType);
		}

		String ordinalKey = makeMethodOrdinalKey(serviceType, methodName, paramCount);
		if (!methodOrdinal.containsKey(ordinalKey)) {
			log.error(String.format("cant find matching method candidate for %s.%s %d params", serviceType, methodName, paramCount));
			return null;
		}
		return methodOrdinal.get(ordinalKey);
	}

	// TODO
	// public static Object encode(Object, encoding) - dispatches appropriately

	static final public String getMsgKey(Message msg) {
		return String.format("msg %s.%s --> %s.%s(%s) - %d", msg.sender, msg.sendingMethod, msg.name, msg.method, Encoder.getParameterSignature(msg.data), msg.msgID);
	}

	static final public String getMsgTypeKey(Message msg) {
		return String.format("msg %s.%s --> %s.%s(%s)", msg.sender, msg.sendingMethod, msg.name, msg.method, Encoder.getParameterSignature(msg.data));
	}

	static final public String getParameterSignature(final Object[] data) {
		if (data == null) {
			return "";
		}

		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < data.length; ++i) {
			if (data[i] != null) {
				Class<?> c = data[i].getClass(); // not all data types are safe
													// toString() e.g.
													// SerializableImage
				if (c == String.class || c == Integer.class || c == Boolean.class || c == Float.class || c == MRLListener.class) {
					ret.append(data[i].toString());
				} else {
					String type = data[i].getClass().getCanonicalName();
					String shortTypeName = type.substring(type.lastIndexOf(".") + 1);
					ret.append(shortTypeName);
				}

				if (data.length != i + 1) {
					ret.append(",");
				}
			} else {
				ret.append("null");
			}

		}
		return ret.toString();

	}

	static public String getServiceType(String inType) {
		if (inType == null) {
			return null;
		}
		if (inType.contains(".")) {
			return inType;
		}
		return String.format("org.myrobotlab.service.%s", inType);
	}

	public static Message gsonToMsg(String gsonData) {
		return gson.fromJson(gsonData, Message.class);
	}

	/**
	 * most lossy protocols need conversion of parameters into correctly typed
	 * elements this method is used to query a candidate method to see if a
	 * simple conversion is possible
	 * 
	 * @return
	 */
	public static boolean isSimpleType(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz) || clazz == String.class;
	}

	public static boolean isWrapper(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz);
	}

	public static boolean isWrapper(String className) {
		return WRAPPER_TYPES_CANONICAL.contains(className);
	}

	// FIXME - axis's Method cache - loads only requested methods
	// this would probably be more gracefull than batch loading as I am doing..
	// http://svn.apache.org/repos/asf/webservices/axis/tags/Version1_2RC2/java/src/org/apache/axis/utils/cache/MethodCache.java
	static public void loadObjectCache(String serviceType) {
		try {
			objectsCached.add(serviceType);
			Class<?> clazz = Class.forName(serviceType);
			Method[] methods = clazz.getMethods();
			for (int i = 0; i < methods.length; ++i) {
				Method m = methods[i];
				Class<?>[] types = m.getParameterTypes();

				String ordinalKey = makeMethodOrdinalKey(serviceType, m.getName(), types.length);
				String methodKey = makeMethodKey(serviceType, m.getName(), types);

				if (!methodOrdinal.containsKey(ordinalKey)) {
					ArrayList<Method> keys = new ArrayList<Method>();
					keys.add(m);
					methodOrdinal.put(ordinalKey, keys);
				} else {
					methodOrdinal.get(ordinalKey).add(m);
				}

				if (log.isDebugEnabled()) {
					log.debug(String.format("loading %s into method cache", methodKey));
				}
				methodCache.put(methodKey, m);
			}
		} catch (Exception e) {
			Logging.logError(e);
		}
	}

	// FIXME !!! - encoding for Message ----> makeMethodKey(Message msg)

	static public String makeMethodKey(String fullObjectName, String methodName, Class<?>[] paramTypes) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < paramTypes.length; ++i) {
			sb.append("/");
			sb.append(paramTypes[i].getCanonicalName());
		}
		return String.format("%s/%s%s", fullObjectName, methodName, sb.toString());
	}

	static public String makeMethodOrdinalKey(String fullObjectName, String methodName, int paramCount) {
		return String.format("%s/%s/%d", fullObjectName, methodName, paramCount);
	}

	// LOSSY Encoding (e.g. xml & gson - which do not encode type information)
	// can possibly
	// give us the parameter count - from the parameter count we can grab method
	// candidates
	// @return is a arraylist of keys !!!

	public static final String msgToBase64(Message msg) {
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		try {
			final ObjectOutputStream objectStream = new ObjectOutputStream(dataStream);
			objectStream.writeObject(msg);
			objectStream.close();
			dataStream.close();
			String base64 = String.format("%s://%s", SCHEME_BASE64, new String(Base64.encodeBase64(dataStream.toByteArray())));
			return base64;
		} catch (Exception e) {
			log.error(String.format("couldnt seralize %s", msg));
			Logging.logError(e);
			return null;
		}
	}

	public static String msgToGson(Message msg) {
		return gson.toJson(msg, Message.class);
	}

	public static boolean setJSONPrettyPrinting(boolean b) {
		if (b) {
			gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").setPrettyPrinting().disableHtmlEscaping().create();
		} else {
			gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").disableHtmlEscaping().create();
		}
		return b;
	}

	// --- xml codec begin ------------------
	// inbound parameters are probably strings or xml bits encoded in some way -
	// need to match
	// ordinal first

	static public String toCamelCase(String s) {
		String[] parts = s.split("_");
		String camelCaseString = "";
		for (String part : parts) {
			camelCaseString = camelCaseString + toCCase(part);
		}
		return String.format("%s%s", camelCaseString.substring(0, 1).toLowerCase(), camelCaseString.substring(1));
	}

	static public String toCCase(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	public final static String toJson(Object o) {
		return gson.toJson(o);
	}

	public final static String toJson(Object o, Class<?> clazz) {
		return gson.toJson(o, clazz);
	}

	public static void toJsonFile(Object o, String filename) throws IOException {
		FileOutputStream fos = new FileOutputStream(new File(filename));
		fos.write(gson.toJson(o).getBytes());
		fos.close();
	}

	// === method signatures begin ===

	static public String toUnderScore(String camelCase) {
		return toUnderScore(camelCase, false);
	}

	static public String toUnderScore(String camelCase, Boolean toLowerCase) {

		byte[] a = camelCase.getBytes();
		boolean lastLetterLower = false;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; ++i) {
			boolean currentCaseUpper = Character.isUpperCase(a[i]);

			Character newChar = null;
			if (toLowerCase != null) {
				if (toLowerCase) {
					newChar = (char) Character.toLowerCase(a[i]);
				} else {
					newChar = (char) Character.toUpperCase(a[i]);
				}
			} else {
				newChar = (char) a[i];
			}

			sb.append(String.format("%s%c", (lastLetterLower && currentCaseUpper) ? "_" : "", newChar));
			lastLetterLower = !currentCaseUpper;
		}

		return sb.toString();

	}

	public static boolean tryParseInt(String string) {
		try {
			Integer.parseInt(string);
			return true;
		} catch (Exception e) {

		}
		return false;
	}

	public static String type(String type) {
		int pos0 = type.indexOf(".");
		if (pos0 > 0) {
			return type;
		}
		return String.format("org.myrobotlab.service.%s", type);
	}
	
	static final String JSON = "application/javascript";
	
	// start fresh :P
	// FIXME should probably use a object factory and interface vs static methods
	static public void write(OutputStream out, Object toEncode) throws IOException{
		write(JSON, out, toEncode);
	}
	
	static public void write(String mimeType, OutputStream out, Object toEncode) throws IOException{
		if (JSON.equals(mimeType)){
			out.write(gson.toJson(toEncode).getBytes());
			//out.flush();
		} else {
			log.error(String.format("write mimeType %s not supported", mimeType));
		}
	}
	

	// === method signatures end ===
}

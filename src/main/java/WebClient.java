import com.sun.deploy.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;


public class WebClient {

    public static final String CHARSET_UTF8 = "UTF-8";
    private static final String DEFAULT_CHARSET = CHARSET_UTF8;
    private static final String METHOD_POST = "POST";
    private static final String METHOD_GET = "GET";


    private static HttpURLConnection getConnection(URL url, String method, String ctype)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "text/xml,text/javascript,text/html");
        conn.setRequestProperty("User-Agent", "Top4Java");
        conn.setRequestProperty("Content-Type", ctype);
        return conn;
    }


    private static byte[] getTextEntry(String fieldName, String fieldValue, String charset)
            throws IOException {
        StringBuilder entry = new StringBuilder();
        entry.append("Content-Disposition:form-data;name=\"");
        entry.append(fieldName);
        entry.append("\"\r\nContent-Type:text/plain\r\n\r\n");
        entry.append(fieldValue);
        return entry.toString().getBytes(charset);
    }

    private static byte[] getFileEntry(String fieldName, String fileName, String mimeType,
                                       String charset) throws IOException {
        StringBuilder entry = new StringBuilder();
        entry.append("Content-Disposition:form-data;name=\"");
        entry.append(fieldName);
        entry.append("\";filename=\"");
        entry.append(fileName);
        entry.append("\"\r\nContent-Type:");
        entry.append(mimeType);
        entry.append("\r\n\r\n");
        return entry.toString().getBytes(charset);
    }

    private static String getResponseCharset(String ctype) {
        String charset = DEFAULT_CHARSET;

        if (!(ctype == null || ctype.length() == 0)) {
            String[] params = ctype.split(";");
            for (String param : params) {
                param = param.trim();
                if (param.startsWith("charset")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2) {
                        if (!(pair[1] == null || pair[1].length() == 0)) {
                            charset = pair[1].trim();
                        }
                    }
                    break;
                }
            }
        }

        return charset;
    }

    private static String getStreamAsString(InputStream stream, String charset) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset));
            StringWriter writer = new StringWriter();

            char[] chars = new char[256];
            int count = 0;
            while ((count = reader.read(chars)) > 0) {
                writer.write(chars, 0, count);
            }

            return writer.toString();
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    private static String getResponseAsString(HttpURLConnection conn) throws IOException {
        String charset = getResponseCharset(conn.getContentType());
        InputStream es = conn.getErrorStream();
        if (es == null) {
            return getStreamAsString(conn.getInputStream(), charset);
        } else {
            String msg = getStreamAsString(es, charset);
            if (msg == null || msg.length() == 0) {
                throw new IOException(conn.getResponseCode() + ":" + conn.getResponseMessage());
            } else {
                throw new IOException(msg);
            }
        }
    }


    public static String doPost(String url, Map<String, String> textParams,
                                Map<String, FileItem> fileParams, String charset) throws IOException, IOException {
        String boundary = System.currentTimeMillis() + ""; // 随机分隔线
        HttpURLConnection conn = null;
        OutputStream out = null;
        String rsp = null;

        try {
            String ctype = "multipart/form-data;charset=" + charset + ";boundary=" + boundary;
            conn = getConnection(new URL(url), METHOD_POST, ctype);
            out = conn.getOutputStream();

            byte[] entryBoundaryBytes = ("\r\n--" + boundary + "\r\n").getBytes(charset);

            // 组装文本请求参数
            Set<Map.Entry<String, String>> textEntrySet = textParams.entrySet();
            for (Map.Entry<String, String> textEntry : textEntrySet) {
                byte[] textBytes = getTextEntry(textEntry.getKey(), textEntry.getValue(), charset);
                out.write(entryBoundaryBytes);
                out.write(textBytes);
            }

            // 组装文件请求参数
            Set<Map.Entry<String, FileItem>> fileEntrySet = fileParams.entrySet();
            for (Map.Entry<String, FileItem> fileEntry : fileEntrySet) {
                FileItem fileItem = fileEntry.getValue();
                byte[] fileBytes = getFileEntry(fileEntry.getKey(), fileItem.getFileName(),
                        fileItem.getMimeType(), charset);
                out.write(entryBoundaryBytes);
                out.write(fileBytes);
                out.write(fileItem.getContent());
            }

            // 添加请求结束标志
            byte[] endBoundaryBytes = ("\r\n--" + boundary + "--\r\n").getBytes(charset);
            out.write(endBoundaryBytes);

            rsp = getResponseAsString(conn);
        } finally {
            if (out != null) {
                out.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }

        return rsp;
    }
}

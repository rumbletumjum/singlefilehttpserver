package io.rkbkr.singlefilehttpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleFileHttpServer {
  private static final Logger logger = LoggerFactory.getLogger(SingleFileHttpServer.class);

  private final byte[] content;
  private final byte[] header;
  private final int port;
  private final String encoding;

  public SingleFileHttpServer(byte[] data, String encoding, String mimeType, int port) {
    this.content = data;
    this.port = port;
    this.encoding = encoding;

    String header =
        "HTTP/1.0 200 OK\r\n"
            + "Server: OneFile 2.0\r\n"
            + "Content-length: "
            + this.content.length
            + "\r\n"
            + "Content-type: "
            + mimeType
            + "; charset="
            + encoding
            + "\r\n\r\n";

    this.header = header.getBytes(StandardCharsets.US_ASCII);
  }

  public SingleFileHttpServer(String data, String encoding, String mimeType, int port)
      throws UnsupportedEncodingException {
    this(data.getBytes(encoding), encoding, mimeType, port);
  }

  public void start() {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try (ServerSocket server = new ServerSocket(this.port)) {
      logger.info("Accepting connections on port " + server.getLocalPort());
      logger.info("Data to be sent: ");
      logger.info(new String(this.content, encoding));

      while (true) {
        try {
          Socket connection = server.accept();
          pool.submit(new HttpHandler(connection));
        } catch (IOException e) {
          logger.warn("Exception accepting connection", e);
        } catch (RuntimeException e) {
          logger.error("Unexpected error", e);
        }
      }
    } catch (IOException e) {
      logger.error("Could not start server", e);
    }
  }

  private class HttpHandler implements Callable<Void> {
    private final Socket connection;

    HttpHandler(Socket connection) {
      this.connection = connection;
    }

    @Override
    public Void call() throws IOException {
      try {
        OutputStream out = new BufferedOutputStream(connection.getOutputStream());
        InputStream in = new BufferedInputStream(connection.getInputStream());

        StringBuilder request = new StringBuilder(80);
        while (true) {
          int c = in.read();
          if (c == '\r' || c == '\n' || c == -1) break;
          request.append((char) c);
        }

        if (request.toString().indexOf("HTTP/") != -1) {
          out.write(header);
        }
        out.write(content);
        out.flush();
      } catch (IOException e) {
        logger.warn("Error writing to client", e);
      } finally {
        connection.close();
      }
      return null;
    }
  }

  public static void main(String[] args) {
    int port;
    try {
      port = Integer.parseInt(args[1]);
      if (port < 1 || port > 65535) port = 80;
    } catch (RuntimeException e) {
      port = 80;
    }

    String encoding = "UTF-8";
    if (args.length > 2) encoding = args[2];

    try {
      Path path = Paths.get(args[0]);
      byte[] data = Files.readAllBytes(path);

      String contentType = URLConnection.getFileNameMap().getContentTypeFor(args[0]);
      SingleFileHttpServer server = new SingleFileHttpServer(data, encoding, contentType, port);
      server.start();
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("Usage: java SingleFileHttpServer filename port encoding");
    } catch (IOException e) {
      logger.error(e.getMessage());
    }
  }
}

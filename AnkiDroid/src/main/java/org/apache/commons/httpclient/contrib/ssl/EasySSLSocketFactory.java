
package org.apache.commons.httpclient.contrib.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

@SuppressWarnings("deprecation") // tracking HTTP transport change in github already
public class EasySSLSocketFactory implements org.apache.http.conn.scheme.SocketFactory, org.apache.http.conn.scheme.LayeredSocketFactory {
    private SSLContext sslcontext = null;


    private static SSLContext createEasySSLContext() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { new EasyX509TrustManager(null) }, null);
            return context;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }


    private SSLContext getSSLContext() throws IOException {
        if (sslcontext == null) {
            sslcontext = createEasySSLContext();
        }
        return sslcontext;
    }


    /**
     * @see org.apache.http.conn.scheme.SocketFactory#connectSocket(java.net.Socket, java.lang.String, int,
     *      java.net.InetAddress, int, org.apache.http.params.HttpParams)
     */
    @Override
    public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort,
                                org.apache.http.params.HttpParams params) throws IOException {
        int connTimeout = org.apache.http.params.HttpConnectionParams.getConnectionTimeout(params);
        int soTimeout = org.apache.http.params.HttpConnectionParams.getSoTimeout(params);
        InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
        SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket());

        if ((localAddress != null) || (localPort > 0)) {
            // we need to bind explicitly
            if (localPort < 0) {
                localPort = 0; // indicates "any"
            }
            InetSocketAddress isa = new InetSocketAddress(localAddress, localPort);
            sslsock.bind(isa);
        }

        sslsock.connect(remoteAddress, connTimeout);
        sslsock.setSoTimeout(soTimeout);
        return sslsock;
    }


    /**
     * @see org.apache.http.conn.scheme.SocketFactory#createSocket()
     */
    @Override
    public Socket createSocket() throws IOException {
        return getSSLContext().getSocketFactory().createSocket();
    }


    /**
     * @see org.apache.http.conn.scheme.SocketFactory#isSecure(java.net.Socket)
     */
    @Override
    public boolean isSecure(Socket socket) throws IllegalArgumentException {
        return true;
    }


    /**
     * @see org.apache.http.conn.scheme.LayeredSocketFactory#createSocket(java.net.Socket, java.lang.String, int,
     *      boolean)
     */
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
    }


    // -------------------------------------------------------------------
    // javadoc in org.apache.http.conn.scheme.SocketFactory says :
    // Both Object.equals() and Object.hashCode() must be overridden
    // for the correct operation of some connection managers
    // -------------------------------------------------------------------

    @Override
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(EasySSLSocketFactory.class));
    }


    @Override
    public int hashCode() {
        return EasySSLSocketFactory.class.hashCode();
    }
}

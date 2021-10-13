# Project description
This is a client python API for HBase thrift2 service. Added exception handling and autoretry, reconnection functions.
## Install
Use pip to install the package (recommand).
```commandline
pip install thbase
```
## Usage
### Single thread
```python
from thbase.thrift2.client import Client
from thbase.config import ClientConfig, TransportType, ProtocolType
from thbase.thrift2.operation import Delete, Scan, Put, Get

if __name__ == '__main__':
    conf = ClientConfig(thrift_host=host,
                        port=port,
                        retry_times=10,
                        retry_timeout=10,
                        transport_type=TransportType.BUFFERED,
                        protocol_type=ProtocolType.BINARY,
                        use_ssl=True,
                        batch_size=10,
                        use_http=True)
    client = Client(conf)
    if client.open_connection():
        table = client.get_table("your_table_name")
        # example for a single operation
        p = Put(row="your_row_key",
                family="column_family",
                qualifier="column_qualifier",
                value="your_value")
        table.put(p)
        
        # example for a batch operation
        put_list = []
        for i in range(100):
            row_key = "row{}".format(i)
            p = Put(row=row_key,
                    family="column_family",
                    qualifier="column_qualifier",
                    value="your_value")
            put_list.append(p)
        table.put_batch(put_list)
        
        # do not forget to close the connection after using
        client.close_connection()
```
###Multi-threaded
The thrift basic transport is not thread-safe. In this case, if you want to parallelize your program, you should create a new connection object for each thread. 
The sample code is:
```python
from thbase.thrift2.client import Client
from thbase.config import ClientConfig, TransportType, ProtocolType
from thbase.thrift2.operation import Delete, Scan, Put, Get
import threading
import logging
 
 
# initialize the logger to check runtime log information for more details about logger usage please refer: https://docs.python.org/2.7/library/logging.html
logging.basicConfig()
 
 
host = your_host
port = your_port
 
 
def demo_func(conf):
    # get the Client object
    client = Client(conf)
 
    # Open the connection
    if client.open_connection():
        
        # get a table object with given table name
        table = client.get_table("your_table_name")
        # single put operation
        p = Put(row="your_row_key",
                family="your_column_family",
                qualifier="your_column_qualifier",
                value="your_data")
        if table.put(p):
            # do sth
        else:
            # do sth
        
        
        # batch put operation
        put_list = []
        for i in range(100):
            row_key = "row{}".format(i)
            p = Put(row=row_key,
                    family="your_column_family",
                    qualifier="your_column_qualifier",
                    value="your_data")
            put_list.append(p)
        if table.put_batch(put_list):
            # do sth
        else:
            # do sth
        
        # single get operation
        g = Get(row=row_key,family="your_column_family",qualifier="your_coloumn_qualifier",max_versions=your_max_version)
        result = table.get(g)
        
        # batch get operation
        get_list = []
        for i in range(10):
            get_list.append(Get(row=row_key,
                                family='0',
                                qualifier=None,
                                max_versions=1))
        table.get_batch(get_list)
        
        # single delete operation
        delete = Delete(row='row10',
                        family='0')
        if table.delete(delete):
            # do sth.
             
        else:
            # do sth.
            
        # delete batch operation
        delete_list = []
        for i in range(10):
            delete_list.append(Delete(row='row{}'.format(i)))
        if table.delete_batch(delete_list):
            # do sth.
        else:
            # do sth.
        
        # scan operation
        scan = Scan(start_row="your_start_row_key",
                    family="your_column_family",
                    qualifier="your_column_qualifier",
                    max_versions="your_max_version",
                    reversed="if_reverse_results")
        results = table.scan(scan=scan)
        print [str(r) for r in results]
        # don't forget to close the connection after using.
        client.close_connection()
 
 
if __name__ == '__main__':
 
    # initialize the client configuration
    conf = ClientConfig(thrift_host=host,  # thrift server address type: str
                        port=9090,  # thrift server port type: int, default 9090
                        retry_times=10,
                        # retry times for reconnection when client lose connnection with the server, type: int, default: 10
                        retry_timeout=10,  # seconds between two reconnection tries, type: int, default: 10
                        transport_type=TransportType.FRAMED,
                        # Use the Enum class, default: TransportType.BUFFERED
                        protocol_type=ProtocolType.BINARY,
                        # Use the relative Enum class, default: ProtocolType.BINARY
                        use_ssl=True,
                        # If True, the Client will use SSL Socket to transport requests to the thrift server
                        batch_size=10,  # The max size of the batch operations
                        use_http=True,
                        )
    # initialize thread list
    thread_list = []
    for _ in range(10):
        x = threading.Thread(target=demo_func, args=(conf,))
        thread_list.append(x)
        x.start()
    for thread in thread_list:
        thread.join()
```
## Source
The github repository is:  
https://github.com/YutSean/thbase

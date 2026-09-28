"""
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
from thbase.config import ClientConfig
from thbase.connection import Connection
from thbase.util.handlers import ExceptionHandler, MessageType
import abc
import logging

logger = logging.getLogger(__name__)


class ClientBase(object):
    """
    Abstract class for both thrift1 and thrift2 client. Implemented with Observer design pattern.
    This class uses a connection object to manage the basic thrift connection with thrift server.
    """
    def __init__(self, conf):
        """
        Initialize the thrift connection and add a new exception handler to deal with exceptions.
        This class should be used by user in NO circumstance!
        Args:
            conf: a customized ClientConfig object.
        """
        if not isinstance(conf, ClientConfig):
            err_str = "Invalid Client Configuration type {}.".format(type(conf))
            logger.error(ValueError(err_str))
            raise ValueError(err_str)
        self.conf = conf
        self.connection = Connection(host=self.conf.host,
                                     port=self.conf.port,
                                     transport_type=self.conf.transport_type,
                                     protocol_type=self.conf.protocol_type,
                                     retry_timeout=self.conf.retry_timeout,
                                     retry_times=self.conf.retry_times,
                                     use_ssl=self.conf.use_ssl,
                                     use_http=self.conf.use_http,
                                     authentication=self.conf.authentication,
                                     keep_alive=self.conf.keep_alive,
                                     )
        self._observers = set()
        self.attach(ExceptionHandler(self))

    def attach(self, observer):
        """
        Add a new observer into the observer set.
        Args:
            observer: object watching on this client.

        Returns:

        """
        self._observers.add(observer)

    def detach(self, observer):
        """
        Remove an observer from the observer set. If the observer is not registered, do nothing.
        Args:
            observer: object in the observer set.

        Returns:

        """
        if observer in self._observers:
            self._observers.discard(observer)

    def notify(self, message_type, value):
        """
        Notify all the observer to handle something.
        Args:
            message_type: an enum defined in util.handlers module.
            value: the data for handling.

        Returns:

        """
        for obr in self._observers:
            obr.handle(message_type, value)

    def open_connection(self):
        """
        This method open the connection with thrift server.
        Raise TTransport exception.
        Returns: True if success, else False.

        """
        try:
            if not self.connection.is_open():
                self.connection.open()
                return self.connection.is_open()
            return True
        except Exception as e:
            self.notify(MessageType.ERROR, e)
            return self.connection.is_open()

    def close_connection(self):
        """
        This method close the current connection. The close() will not raise any exception and will always success.
        Returns: None

        """
        if self.connection.is_open():
            self.connection.close()

    @abc.abstractmethod
    def _put_row(self, table_name, put):
        """
        Send a single put request to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: Should be invoked by a Table object.
            put: a Put object.

        Returns:
            True if the operation successes, else False.

        """
        pass

    @abc.abstractmethod
    def _put_rows(self, table_name, put_list):
        """
        Send a batch of put requests to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            put_list: a list of Put objects.

        Returns:
            True if the operation successes, else False.

        """
        pass

    @abc.abstractmethod
    def _get_row(self, table_name, get):
        """
        Send a single get request to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            get: a Get object.

        Returns:
            A list of Cells if success. An empty list if the operation fails or the target cell does not exists.
        """
        pass

    @abc.abstractmethod
    def _get_rows(self, table_name, get_list):
        """
        Send a batch of get requests to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            get_list: a list of Get objects.

        Returns:
            A list of Cells if success. An empty list if the operation fails or the target cells do not exists.
        """
        pass

    @abc.abstractmethod
    def _scan(self, table_name, scan):
        """
        Send a scan request to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            scan: a Scan object.

        Returns:
            A list of Cells if success. An empty list if the operation fails or the target cells do not exists.
        """
        pass

    @abc.abstractmethod
    def _delete_row(self, table_name, delete):
        """
        Send a delete request to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            delete: a Delete object.

        Returns:
            True if successes, else False.
        """
        pass

    @abc.abstractmethod
    def _delete_batch(self, table_name, delete_list):
        """
        Send a batch of delete requests to thrift server. Only should be invoked by a Table object.
        Args:
            table_name: a str representation of Table name, including the namespace part.
            delete_list: a list of Delete objects.

        Returns:
            True if successes, else False.
        """
        pass

    @abc.abstractmethod
    def _refresh_client(self):
        """
        Reconstruct a client, be used when the client reconnects to the thrift server.
        Returns:
            None
        """
        pass

    @abc.abstractmethod
    def get_table(self, table_name):
        """
        Get a Table object of given table name.
        Args:
            table_name: a str representation of Table name, including the namespace part.

        Returns:
            a Table object.
        """
        pass

    @abc.abstractmethod
    def create_table(self, desc, split_keys):
        """
        Create a table.
        Args:
            desc: a TTableDescriptor that contains the table meta data.
            split_keys: keys for pre-splitting

        Returns:
            True if success, else False.
        """
        pass

    @abc.abstractmethod
    def delete_table(self, table_name):
        """
        Delete a table. The table should be disabled first.
        Args:
            table_name: The corresponding table name.

        Returns:

        """
        pass

    @abc.abstractmethod
    def enable_table(self, table_name):
        """
        Enable a table. If the table is already enabled, will return an error.
        Args:
            table_name: The name of corresponding table.

        Returns:

        """
        pass

    @abc.abstractmethod
    def disable_table(self, table_name):
        """
        Disable a table. If the table is already disabled, will return an error.
        Args:
            table_name: The name of corresponding table.

        Returns:

        """
        pass

    @abc.abstractmethod
    def truncate_table(self, table_name, preserve_splits):
        """
        Drop a table and recreate it.
        Args:
            table_name: The name of the table.
            preserve_splits: If the splits need to be preserved.

        Returns:

        """
        pass

    @abc.abstractmethod
    def is_enabled(self, table_name):
        """
        Check if the given table is enabled.
        Args:
            table_name: TTableName

        Returns:

        """
        pass

    @abc.abstractmethod
    def get_tableDescriptor(self, table_name):
        """
        Get a TTableDescriptor of a specific table.
        Args:
            table_name:

        Returns:

        """
        pass

    @abc.abstractmethod
    def modify_columnFamily(self, table_name, desc):
        """
        Modify the attribute of a column family.
        Args:
            table_name:
            desc:

        Returns:

        """
        pass

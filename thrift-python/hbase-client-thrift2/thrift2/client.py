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
from thbase.hbase import THBaseService
from thbase.hbase.ttypes import TTableDescriptor, TPermissionScope
from thbase.hbase.ttypes import TColumnFamilyDescriptor
from thbase.hbase.ttypes import TAccessControlEntity
from thbase.clientbase import ClientBase
from thbase.thrift2.table import Table
from thbase.util.executor import Executor
from thbase.util.bytes import to_str
from thbase.util.builder import ColumnDescriptorBuilder
from thbase.util.builder import TableDescriptorBuilder
from thbase.util import type_check
from thbase.util import check_none
from thbase.util import str_to_tablename
import logging

logger = logging.getLogger(__name__)


class Client(ClientBase):
    """
    Client implemented by thrift2 API.
    User should not invoke methods of a Client object directly.
    This class does not provide retry mechanism, reconnection mechanism and exception handling.
    Please not use it directly.
    """
    def __init__(self, conf):
        super(Client, self).__init__(conf=conf)
        self.client = THBaseService.Client(self.connection.protocol)
        self.executor = Executor(self.conf.retry_times, self.conf.retry_timeout, master=self)

    def _put_row(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        put = kwargs['put']
        return self.executor.call(lambda: self.client.put(table_name, put.core))

    def _put_rows(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        puts = kwargs['puts']
        return self.executor.call(lambda: self.client.putMultiple(table_name, [put.core for put in puts]))

    def _get_row(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        get = kwargs['get']
        result = self.executor.call(lambda: self.client.get(table_name, get.core))
        return [result]

    def _get_rows(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        gets = kwargs['gets']
        return self.executor.call(lambda: self.client.getMultiple(table_name, [get.core for get in gets]))

    def _scan(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        scan = kwargs['scan']
        return self.executor.call(lambda: self.client.getScannerResults(table_name, scan.core, scan.num_rows))

    def _delete_row(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        delete = kwargs['delete']
        return self.executor.call(lambda: self.client.deleteSingle(table_name, delete.core))

    def _delete_batch(self, **kwargs):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        table_name = kwargs['table_name']
        deletes = kwargs['deletes']
        return self.executor.call(lambda: self.client.deleteMultiple(table_name, [delete.core for delete in deletes]))

    def _refresh_client(self):
        """
        Private method, should not be used by users.
        Args:
            **kwargs:

        Returns:

        """
        self.client = THBaseService.Client(self.connection.protocol)

    def get_table(self, table_name):
        """
        Acquire a table object to use functional methods.
        Args:
            **kwargs:

        Returns:

        """
        return Table(table_name=table_name, client=self)

    def create_table(self, desc, split_keys):
        """

        Args:
            desc: TTableDescriptor, which contains the meta information of the table to create.
            split_keys: split keys for table pre-split.

        Returns: True if success, else False.

        """
        type_check(desc, TTableDescriptor)
        type_check(split_keys, list)
        return self.executor.call(lambda: self.client.createTable(desc, split_keys))

    def delete_table(self, table_name):
        """

        Args:
            table_name: The name of the table that need to be removed.

        Returns: True if success, else False.

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.deleteTable(tn))

    def enable_table(self, table_name):
        """

        Args:
            table_name: The name of the table that need to be enabled.
            If the table is already enabled, it will raise an Error.
        Returns: True if success, else False.

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.enableTable(tn))

    def disable_table(self, table_name):
        """

        Args:
            table_name: The name of the table that need to be disabled.
            If the table is already enabled, it will raise an Error.
        Returns: True if success, else False.

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.disableTable(tn))

    def truncate_table(self, table_name, preserve_splits):
        """

        Args:
            table_name:
            preserve_splits:

        Returns:

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.truncateTable(tn, preserve_splits))

    def is_enabled(self, table_name):
        """
        Check if the table is enabled.
        Args:
            table_name:

        Returns:

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.isTableEnabled(tn))

    def get_tableDescriptor(self, table_name):
        """

        Args:
            table_name:

        Returns:

        """
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.getTableDescriptor(tn))

    def get_columnDescriptor(self, table_name, cf):
        """
        Return a column descriptor for specific table with the given cf name.
        Args:
            table_name:
            cf:

        Returns:

        """
        type_check(cf, str)
        td = self.get_tableDescriptor(table_name)
        check_none(td, "Unknown table.")
        for col in td.columns:
            if to_str(col.name) == cf:
                return col
        raise RuntimeError("The table does not contain a column family with name {}".format(cf))

    def get_columnBuilder(self, table_name, cf):
        """
        Acquire a column descriptor of a specific cf.
        Args:
            table_name:
            cf:

        Returns:

        """
        desc = self.get_columnDescriptor(table_name, cf)
        builder = ColumnDescriptorBuilder('')
        builder.copy_from_exist(desc)
        return builder

    def modify_columnFamily(self, table_name, desc):
        """

        Args:
            table_name:
            desc:

        Returns:

        """
        type_check(desc, TColumnFamilyDescriptor)
        tn = str_to_tablename(table_name)
        return self.executor.call(lambda: self.client.modifyColumnFamily(tn, desc))

    def get_tableBuilder(self, table_name):
        """

        Args:
            table_name:

        Returns:

        """
        builder = TableDescriptorBuilder(table_name)
        desc = self.get_tableDescriptor(table_name)
        builder.copy_from_exist(desc)
        return builder

    def modify_table(self, table_descriptor):
        """

        Args:
            table_descriptor:

        Returns:

        """
        type_check(table_descriptor, TTableDescriptor)
        return self.executor.call(lambda: self.client.modifyTable(table_descriptor))

    def grant(self, info):
        """

        Args:
            info:

        Returns:

        """
        type_check(info, TAccessControlEntity)
        return self.executor.call(lambda: self.client.grant(info))

    def revoke(self, info):
        """

        Args:
            info:

        Returns:

        """
        type_check(info, TAccessControlEntity)
        return self.executor.call(lambda: self.client.revoke(info))

    def get_user_permissions(self, domain_name):
        """

        Args:
            domain_name:
            scope:

        Returns:

        """
        type_check(domain_name, str)
        scope = TPermissionScope.TABLE if domain_name[0] != '@' else TPermissionScope.NAMESPACE
        return self.executor.call(lambda: self.client.getUserPermission(domain_name, scope))

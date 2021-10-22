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
from typing import List
from thbase.thrift2.operation import Get, Put, Delete, Scan
from thbase.thrift2.cell import Cell
from thbase.hbase.ttypes import TResult
from thbase.util import type_check
from thbase.util.executor import Executor
from thbase.util.bytes import to_bytes
import logging

logger = logging.getLogger(__name__)


class Table(object):
    """
    This is a class for doing operations on a specific table.
    The object is deprecated to be created by users.
    Use Client.get_table(table_name) method to create a table object.
    """
    def __init__(self, table_name, client):
        self._name = to_bytes(table_name)
        self._client = client
        self.conf = client.conf
        self.executor = Executor(self.conf.retry_times, self.conf.retry_timeout, master=self._client)

    @property
    def name(self):
        # type: () -> str
        return self._name

    def put(self, put):
        # type: (Put) -> bool
        """
        Send a single Put operation to the thrift server.
        Args:
            put: A customized Get object.

        Returns: True if successes, False otherwise.

        """
        type_check(put, Put)
        return self.executor.call(lambda: self._client._put_row(table_name=self.name, put=put))

    def put_batch(self, puts):
        # type: (List[Put]) -> bool
        """
        Send multiple Put requests to the server at one time.
        The requests will be sent batch by batch.
        The logger will log the failed position if one batch of requests failed.
        Args:
            puts: A list of Put objects

        Returns: True if successes, False otherwise.

        """
        type_check(puts, list)
        for put in puts:
            type_check(put, Put)
        for i in range(0, len(puts), self.conf.batch_size):
            result = self._client._put_rows(table_name=self.name, puts=puts[i: i + self.conf.batch_size])
            if not result:
                logger.error("An error occurs at index {}, the Put requests after {} (inclusive) failed.".format(i, i))
                return False
        return True

    def get(self, get):
        # type: (Get) -> List[Cell]
        """
        Send a single Get operation to the thrift server.
        Args:
            get: A customized Get object.

        Returns: If success: a list of cells.
                 If success but not matched data: an empty list.
                 If get failed: False.

        """
        type_check(get, Get)
        result = self._client._get_row(table_name=self.name, get=get)
        # if result is False, that means the operation failed after retry N times.
        # if result is [], that means there is no matched cell in hbase.
        if not result:
            return []
        return self._results_format(result)

    def get_batch(self, gets):
        # type: (List[Get]) -> List[Cell]
        """
        Send multiple Get requests to the server at one time.
        The requests will be sent batch by batch.
        The logger will log the failed position if one batch of requests failed.
        Args:
            gets: A list of Get objects.

        Returns: If success: a list of cells.
                 If success but not matched data: an empty list.
                 If get failed: False.
                 If partly success, a part result will be returned.

        """
        type_check(gets, (list, tuple))
        for get in gets:
            type_check(get, Get)
        result_list = []
        for i in range(0, len(gets), self.conf.batch_size):
            result = self._client._get_rows(table_name=self.name, gets=gets[i: i + self.conf.batch_size])
            # if result == False, it shows that the operation failed.
            # The task should stop and return the successful part.
            if result is False:
                return self._results_format(result_list)
            elif len(result) > 0:
                result_list += result
        return self._results_format(result_list)

    def scan(self, scan):
        # type: (Scan) -> List[Cell]
        """
        Send a Scan request to the thrift server.
        Args:
            scan: A single Scan object.

        Returns:If success: a list of cells.
                 If success but not matched data: an empty list.
                 If the start row do not exists, it will raise an IllegalArgument error.
                 If scan failed: False.

        """
        type_check(scan, Scan)
        return self._results_format(self._client._scan(table_name=self.name, scan=scan))

    def delete(self, delete):
        # type: (Delete) -> bool
        """
        Send a Delete request to the thrift server.
        Args:
            delete: a single Delete object

        Returns: True if successes, False otherwise.

        """
        type_check(delete, Delete)
        return self._client._delete_row(table_name=self.name, delete=delete)

    def delete_batch(self, batch):
        # type: (List[Delete]) -> bool
        """
        Send a list of Delete requests to the thrift server.
        The requests will be sent batch by batch.
        The logger will log the failed position if one batch of requests failed.
        Args:
            batch: a list of Delete objects.

        Returns: True if successes, False otherwise.

        """
        type_check(batch, (list, tuple))
        for delete in batch:
            type_check(delete, Delete)
        for i in range(0, len(batch), self.conf.batch_size):
            if not self._client._delete_batch(table_name=self.name,
                                              deletes=batch[i: i + self.conf.batch_size]):
                logger.error("Delete_batch failed at index {}, the delete requests after {} (inclusive) are not sent.".format(i, i))
                return False
        return True

    def _results_format(self, results):
        # type: (List[TResult]) -> List[Cell]
        """
        @Deprecated
        Inner util method. Should not be used by user.
        Transform the thrift result to a series of Cell objects.
        Args:
            results: a list of TResult.

        Returns: an empty list if there is no results or a list of Cell objects.

        """
        result_list = []  # type: List[Cell]
        if not results or len(results) == 0:
            return []
        for result in results:
            for cv in iter(result.columnValues):
                result_list.append(Cell(self._name, result.row, cv.family, cv.qualifier, cv.value, cv.timestamp))
        return result_list

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
from thbase.util import check_none
from thbase.util.bytes import to_bytes
from typing import List, Union
from thbase.hbase.ttypes import TGet, TDelete, TScan, TPut, TColumnValue, TColumn


class Operation(object):
    """
    Basic object represent a single HBase operation.
    """
    def __init__(self, row,  # type: Union[None, str]
                 family,  # type: Union[None, str]
                 qualifier,  # type: Union[None, str]
                 value,  # type: Union[None, str]
                 ):
        self.row = to_bytes(row)
        self.family = to_bytes(family)
        self.qualifier = to_bytes(qualifier)
        self.value = to_bytes(value)


class Get(Operation):

    def __init__(self, row=None,  # type: Union[str]
                 family=None,  # type: Union[None, str]
                 qualifier=None,  # type: Union[None, str, List[str]]
                 value=None,  # type: Union[None, str]
                 max_versions=None  # type: Union[None, int]
                 ):
        super(Get, self).__init__(row, family, qualifier, value)
        check_none(self.row, "Row cannot be none for Get operation.")
        self.maxVersions = max_versions
        self.core = TGet(row=self.row,
                         columns=_column_format(family, qualifier),
                         timestamp=None,
                         timeRange=None, maxVersions=self.maxVersions)


class Delete(Operation):

    def __init__(self, row=None,  # type: Union[str]
                 family=None,  # type: Union[None, str]
                 qualifier=None,  # type: Union[None, str]
                 value=None,  # type: Union[None, str]
                 ):
        super(Delete, self).__init__(row, family, qualifier, value)
        check_none(self.row, "Row cannot be none for Delete operation.")
        self.core = TDelete(
            row=self.row,
            columns=_column_format(self.family, self.qualifier),
        )


class Scan(Operation):

    def __init__(self, start_row=None,  # type: Union[None, str]
                 family=None,  # type: Union[None, str]
                 qualifier=None,  # type: Union[None, str, List[str]]
                 stop_row=None,  # type: Union[None, str]
                 num_rows=5000,  # type: int
                 max_versions=None,  # type: Union[None, int]
                 reversed=False,  # type: Union[bool]
                 ):
        super(Scan, self).__init__(start_row, family, qualifier, None)
        self.reversed = reversed
        self.stop_row = to_bytes(stop_row)
        self.num_rows = num_rows
        self.core = TScan(
            startRow=self.row,
            stopRow=self.stop_row,
            columns=_column_format(self.family, self.qualifier),
            maxVersions=max_versions,
            reversed=self.reversed,
        )


class Put(Operation):

    def __init__(self,
                 row=None,  # type: Union[str]
                 family=None,  # type: Union[None, str]
                 qualifier=None,  # type: Union[None, str, List[str]]
                 value=None,  # type: Union[str, List[str]]
                 ):
        super(Put, self).__init__(row, family, qualifier, value)
        check_none(self.row, "Row cannot be none for Put operation.")
        check_none(self.value, "Value cannot be none for Put operation.")
        column_values = []
        columns = _column_format(family, qualifier)
        if isinstance(value, str):
            for col in columns:
                column_values.append(TColumnValue(
                    family=to_bytes(col.family),
                    qualifier=to_bytes(col.qualifier),
                    value=self.value
                ))
        elif isinstance(value, list) or isinstance(value, tuple):
            if len(columns) != len(value):
                raise ValueError("The number of columns mismatches the number of value list.")
            for i, col in enumerate(columns):
                column_values.append(TColumnValue(
                    family=col.family,
                    qualifier=col.qualifier,
                    value=to_bytes(value[i])
                ))
        self.core = TPut(
            row=self.row,
            columnValues=column_values
        )


def _column_format(family, qualifier):
    # type: (str, Union[None, str, List[str]]) -> Union[None, List[TColumn]]
    """
    Util method to get columns from given column family and qualifier.
    If the family is None, this method will return None.
    Args:
        family: name of column family.
        qualifier: name of column qualifier, it can be a str, None or a list of strs.

    Returns: a list of combined columns.

    """
    if family is None:
        return None
    if not isinstance(family, str) and not isinstance(family, bytes):
        raise ValueError("A family name must be a str object, but got {}".format(type(family)))
    family_bytes = to_bytes(family)
    if qualifier is None:
        return [TColumn(family=family_bytes)]
    if isinstance(qualifier, str):
        return [TColumn(family=family_bytes, qualifier=to_bytes(qualifier))]
    if isinstance(qualifier, list) or isinstance(qualifier, tuple):
        cols = []
        for cq in qualifier:
            if isinstance(cq, str) or cq is None:
                cols.append(TColumn(family=family_bytes, qualifier=to_bytes(cq)))
            else:
                raise ValueError("Qualifier should be None, str or a list (tuple) of str")

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
from enum import Enum
from thbase.hbase.ttypes import TApplicationException, TIOError, TIllegalArgument
from thrift.transport.TTransport import TTransportException
import abc
import logging
logger = logging.getLogger(__name__)


class Observer(object):
    """
    Abstract class for Observer design pattern.
    """
    def __init__(self, target):
        self.target = target

    @abc.abstractmethod
    def handle(self, **kwargs):
        pass


MessageType = Enum('MessageType', ["ERROR"])


class ExceptionHandler(Observer):
    """
    This class responses to all exceptions that caught in the client operations,
    including network exceptions, thrift exceptions and HBase exceptions.
    """
    def __init__(self, target):
        super(ExceptionHandler, self).__init__(target)

    def handle(self, message_type, value):
        """
        The main method to deal with Exceptions.
        TODO:Support more kinds of Exceptions.
        Args:
            message_type: Defined by the enum MessageType.
            value: The message should be scoped with.

        Returns:True if the exception fixed. If not, the exception will be raised further.

        """
        if message_type not in MessageType:
            raise ValueError("Unknown message type.")
        if message_type == MessageType.ERROR:
            if isinstance(value, TIOError):
                error_str = "Connection errors occurred between thrift server and hbase server." \
                            "The error message is: {}".format(value.message)
                try:
                    if value.canRetry:
                        logger.warning(error_str + " The error may be solved by retrying."
                                                   " Client will resend the request.")
                        return True
                    else:
                        logger.error(error_str + " The error cannot be solved by resend the request, client will shutdown.")
                        raise value
                except AttributeError:
                    raise AttributeError("The IOError does not contain the canRetry mark. The error will be raised. "
                                         "Please check if the client version is too old.")
            if isinstance(value, TTransportException):
                if value.type == TTransportException.NOT_OPEN or value.type == TTransportException.TIMED_OUT:
                    logger.warning("A transport error occurs, the message is: {}".format(value.message))
                    logger.warning("System will try to rebuild the connection to solve it.")
                    self.target.connection._reconnect()
                    self.target._refresh_client()
                    return True
                if value.type == TTransportException.ALREADY_OPEN:
                    logger.error("A transport error occurs. The message is: {}".format(value.message))
                if value.type == TTransportException.INVALID_CLIENT_TYPE:
                    logger.error("A transport error occurs. The message is: {}".format(value.message))
                if value.type == TTransportException.NEGATIVE_SIZE:
                    logger.error("The server read a frame with negative length. " +
                                 "Please check your encoding charset and data format.\n" +
                                 "Detailed information is {}.".format(value.message))
                if value.type == TTransportException.SIZE_LIMIT:
                    logger.error("A transport error occurs. The message is: {}".format(value.message))
                if value.type == TTransportException.END_OF_FILE:
                    logger.error("A transport error occurs. The message is: {}".format(value.message))
                if value.type == TTransportException.UNKNOWN:
                    messages = ["Unknown error occurs with tcp transport. ",
                                "This may occur with the following reasons:\n",
                                "1. There is a transport or protocol mismatch between the server and the client.\n",
                                "2. The queue of the server is full.\n",
                                "3. The volume of the sent requests exceed the volume limit of the server.\n"]
                    logger.error("".join(messages))
                raise value
            elif isinstance(value, TApplicationException):
                if value.type == TApplicationException.INVALID_MESSAGE_TYPE:
                    logger.error("An thrift internal error occurs. The message is: {}".format(value.message))
                if value.type == TApplicationException.BAD_SEQUENCE_ID:
                    logger.error("An thrift internal error occurs. The message is: {}".format(value.message))
                if value.type == TApplicationException.INVALID_PROTOCOL:
                    logger.error("An thrift internal error occurs. The message is: {}".format(value.message))
                if value.type == TApplicationException.INVALID_TRANSFORM:
                    logger.error("An thrift internal error occurs. The message is: {}".format(value.message))
                if value.type == TApplicationException.INTERNAL_ERROR:
                    logger.error("An thrift internal error occurs. The message is: {}".format(value.message))
                if value.type == TApplicationException.UNKNOWN_METHOD:
                    logger.error("{}. \n" +
                                 "This error happens when the client code outdated. Please update the client code "
                                 "first.\n" +
                                 "If the problem still exists, please contact with the responsive person.\n")
                raise value
            elif isinstance(value, TIllegalArgument):
                logger.error(value.message)
                logger.error("This error is usually caused by invalid arguments. "
                             "For example, in the Scan operation the start row does not exist. "
                             "Please check the attributes of your Operation object and try again.")
                raise value


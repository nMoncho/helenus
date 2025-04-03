/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

sealed abstract class CassandraException(message: String, cause: Throwable)
    extends RuntimeException(message, cause)

class SessionOpenException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class SessionClosingException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class InvalidStatementException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class StatementExecutionException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class InvalidMappingException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class PaginationException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

class GenericCassandraException(message: String, cause: Throwable)
    extends CassandraException(message, cause)

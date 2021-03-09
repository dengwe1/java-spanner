/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/spanner/admin/database/v1/backup.proto

package com.google.spanner.admin.database.v1;

public interface ListBackupsRequestOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:google.spanner.admin.database.v1.ListBackupsRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   *
   *
   * <pre>
   * Required. The instance to list backups from.  Values are of the
   * form `projects/&lt;project&gt;/instances/&lt;instance&gt;`.
   * </pre>
   *
   * <code>
   * string parent = 1 [(.google.api.field_behavior) = REQUIRED, (.google.api.resource_reference) = { ... }
   * </code>
   *
   * @return The parent.
   */
  java.lang.String getParent();
  /**
   *
   *
   * <pre>
   * Required. The instance to list backups from.  Values are of the
   * form `projects/&lt;project&gt;/instances/&lt;instance&gt;`.
   * </pre>
   *
   * <code>
   * string parent = 1 [(.google.api.field_behavior) = REQUIRED, (.google.api.resource_reference) = { ... }
   * </code>
   *
   * @return The bytes for parent.
   */
  com.google.protobuf.ByteString getParentBytes();

  /**
   *
   *
   * <pre>
   * An expression that filters the list of returned backups.
   * A filter expression consists of a field name, a comparison operator, and a
   * value for filtering.
   * The value must be a string, a number, or a boolean. The comparison operator
   * must be one of: `&lt;`, `&gt;`, `&lt;=`, `&gt;=`, `!=`, `=`, or `:`.
   * Colon `:` is the contains operator. Filter rules are not case sensitive.
   * The following fields in the
   * [Backup][google.spanner.admin.database.v1.Backup] are eligible for
   * filtering:
   *   * `name`
   *   * `database`
   *   * `state`
   *   * `create_time`  (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `expire_time`  (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `version_time` (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `size_bytes`
   * You can combine multiple expressions by enclosing each expression in
   * parentheses. By default, expressions are combined with AND logic, but
   * you can specify AND, OR, and NOT logic explicitly.
   * Here are a few examples:
   *   * `name:Howl` - The backup's name contains the string "howl".
   *   * `database:prod`
   *          - The database's name contains the string "prod".
   *   * `state:CREATING` - The backup is pending creation.
   *   * `state:READY` - The backup is fully created and ready for use.
   *   * `(name:howl) AND (create_time &lt; &#92;"2018-03-28T14:50:00Z&#92;")`
   *          - The backup name contains the string "howl" and `create_time`
   *              of the backup is before 2018-03-28T14:50:00Z.
   *   * `expire_time &lt; &#92;"2018-03-28T14:50:00Z&#92;"`
   *          - The backup `expire_time` is before 2018-03-28T14:50:00Z.
   *   * `size_bytes &gt; 10000000000` - The backup's size is greater than 10GB
   * </pre>
   *
   * <code>string filter = 2;</code>
   *
   * @return The filter.
   */
  java.lang.String getFilter();
  /**
   *
   *
   * <pre>
   * An expression that filters the list of returned backups.
   * A filter expression consists of a field name, a comparison operator, and a
   * value for filtering.
   * The value must be a string, a number, or a boolean. The comparison operator
   * must be one of: `&lt;`, `&gt;`, `&lt;=`, `&gt;=`, `!=`, `=`, or `:`.
   * Colon `:` is the contains operator. Filter rules are not case sensitive.
   * The following fields in the
   * [Backup][google.spanner.admin.database.v1.Backup] are eligible for
   * filtering:
   *   * `name`
   *   * `database`
   *   * `state`
   *   * `create_time`  (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `expire_time`  (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `version_time` (and values are of the format YYYY-MM-DDTHH:MM:SSZ)
   *   * `size_bytes`
   * You can combine multiple expressions by enclosing each expression in
   * parentheses. By default, expressions are combined with AND logic, but
   * you can specify AND, OR, and NOT logic explicitly.
   * Here are a few examples:
   *   * `name:Howl` - The backup's name contains the string "howl".
   *   * `database:prod`
   *          - The database's name contains the string "prod".
   *   * `state:CREATING` - The backup is pending creation.
   *   * `state:READY` - The backup is fully created and ready for use.
   *   * `(name:howl) AND (create_time &lt; &#92;"2018-03-28T14:50:00Z&#92;")`
   *          - The backup name contains the string "howl" and `create_time`
   *              of the backup is before 2018-03-28T14:50:00Z.
   *   * `expire_time &lt; &#92;"2018-03-28T14:50:00Z&#92;"`
   *          - The backup `expire_time` is before 2018-03-28T14:50:00Z.
   *   * `size_bytes &gt; 10000000000` - The backup's size is greater than 10GB
   * </pre>
   *
   * <code>string filter = 2;</code>
   *
   * @return The bytes for filter.
   */
  com.google.protobuf.ByteString getFilterBytes();

  /**
   *
   *
   * <pre>
   * Number of backups to be returned in the response. If 0 or
   * less, defaults to the server's maximum allowed page size.
   * </pre>
   *
   * <code>int32 page_size = 3;</code>
   *
   * @return The pageSize.
   */
  int getPageSize();

  /**
   *
   *
   * <pre>
   * If non-empty, `page_token` should contain a
   * [next_page_token][google.spanner.admin.database.v1.ListBackupsResponse.next_page_token]
   * from a previous
   * [ListBackupsResponse][google.spanner.admin.database.v1.ListBackupsResponse]
   * to the same `parent` and with the same `filter`.
   * </pre>
   *
   * <code>string page_token = 4;</code>
   *
   * @return The pageToken.
   */
  java.lang.String getPageToken();
  /**
   *
   *
   * <pre>
   * If non-empty, `page_token` should contain a
   * [next_page_token][google.spanner.admin.database.v1.ListBackupsResponse.next_page_token]
   * from a previous
   * [ListBackupsResponse][google.spanner.admin.database.v1.ListBackupsResponse]
   * to the same `parent` and with the same `filter`.
   * </pre>
   *
   * <code>string page_token = 4;</code>
   *
   * @return The bytes for pageToken.
   */
  com.google.protobuf.ByteString getPageTokenBytes();
}

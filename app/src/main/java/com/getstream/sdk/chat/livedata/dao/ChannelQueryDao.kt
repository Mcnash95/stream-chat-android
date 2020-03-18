package com.getstream.sdk.chat.livedata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getstream.sdk.chat.livedata.entity.ChannelQuery

@Dao
interface ChannelQueryDao {
    /*
    - query channels -> write the query, write many channels
    - notification.new event -> update a single channel
    - offline read flow -> query id based lookup, read a list of channels
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: ChannelQuery)

    @Query(
        "SELECT * FROM stream_channel_query " +
                "WHERE stream_channel_query.id=:id"
    )
    suspend fun select(id: String): ChannelQuery?
}
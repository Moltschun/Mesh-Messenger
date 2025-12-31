package com.example.meshmessenger

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessagesComparator()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isSentByMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textBody: TextView = itemView.findViewById(R.id.text_message_body)
        private val textTime: TextView = itemView.findViewById(R.id.text_message_time)
        private val textStatus: TextView = itemView.findViewById(R.id.text_message_status)

        fun bind(message: Message) {
            textBody.text = message.text
            textTime.text = message.timestamp

            when (message.status) {
                MessageStatus.SENDING -> {
                    textStatus.text = "..."
                    textStatus.setTextColor(Color.GRAY)
                }
                MessageStatus.SENT -> {
                    textStatus.text = "✓"
                    textStatus.setTextColor(Color.BLUE)
                }
                MessageStatus.ERROR -> {
                    textStatus.text = "!"
                    textStatus.setTextColor(Color.RED)
                }
                else -> textStatus.text = ""
            }
        }
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textBody: TextView = itemView.findViewById(R.id.text_message_body)
        private val textTime: TextView = itemView.findViewById(R.id.text_message_time)

        fun bind(message: Message) {
            textBody.text = message.text
            textTime.text = message.timestamp
        }
    }

    class MessagesComparator : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
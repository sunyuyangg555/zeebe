package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.clustering.raft.JoinResponseDecoder.MembersDecoder.*;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.BooleanType;
import org.camunda.tngp.clustering.raft.JoinResponseDecoder;
import org.camunda.tngp.clustering.raft.JoinResponseDecoder.MembersDecoder;
import org.camunda.tngp.clustering.raft.JoinResponseEncoder;
import org.camunda.tngp.clustering.raft.JoinResponseEncoder.MembersEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class JoinResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final JoinResponseDecoder bodyDecoder = new JoinResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final JoinResponseEncoder bodyEncoder = new JoinResponseEncoder();

    protected int id = -1;
    protected int term = -1;

    protected long configurationEntryPosition = -1L;
    protected int configurationEntryTerm = -1;
    protected boolean succeeded;
    protected List<Member> members = new CopyOnWriteArrayList<>();

    public int id()
    {
        return id;
    }

    public JoinResponse id(final int id)
    {
        this.id = id;
        return this;
    }

    public int term()
    {
        return term;
    }

    public JoinResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean succeeded()
    {
        return succeeded;
    }

    public JoinResponse succeeded(final boolean succeeded)
    {
        this.succeeded = succeeded;
        return this;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public JoinResponse configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public JoinResponse configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public JoinResponse members(final List<Member> members)
    {
        this.members.clear();
        this.members.addAll(members);
        return this;
    }

    @Override
    public int getLength()
    {
        final int size = members != null ? members.size() : 0;

        int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
        length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            length += member.endpoint().hostLength();
        }

        return length;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .id(id)
            .term(term)
            .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE)
            .configurationEntryPosition(configurationEntryPosition)
            .configurationEntryTerm(configurationEntryTerm);

        final int size = members != null ? members.size() : 0;

        final MembersEncoder encoder = bodyEncoder.membersCount(size);
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final Endpoint endpoint = member.endpoint();

            encoder.next()
                .port(endpoint.port())
                .putHost(endpoint.getHostBuffer(), 0, endpoint.hostLength());
        }
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        id = bodyDecoder.id();
        term = bodyDecoder.term();
        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;
        configurationEntryPosition = bodyDecoder.configurationEntryPosition();
        configurationEntryTerm = bodyDecoder.configurationEntryTerm();

        members.clear();

        final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();
        while (iterator.hasNext())
        {
            final MembersDecoder decoder = iterator.next();

            final Member member = new Member();
            member.endpoint().port(decoder.port());

            final int hostLength = decoder.hostLength();
            final MutableDirectBuffer endpointBuffer = member.endpoint().getHostBuffer();
            member.endpoint().hostLength(hostLength);
            decoder.getHost(endpointBuffer, 0, hostLength);

            // TODO: make this garbage free
            members.add(member);
        }
    }

    public void reset()
    {
        id = -1;
        term = -1;
        configurationEntryPosition = -1L;
        configurationEntryTerm = -1;
        succeeded = false;
        members.clear();
    }

}
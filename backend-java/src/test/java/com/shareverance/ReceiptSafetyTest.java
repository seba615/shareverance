package com.shareverance;

import com.shareverance.model.*;
import com.shareverance.repository.*;
import com.shareverance.repository.mock.MockReceiptRepository;
import com.shareverance.service.AuthService;
import com.shareverance.service.impl.ReceiptServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptSafetyTest {
    @TempDir Path temp;
    Path root;
    ReceiptRepository receipts;
    ExpenseRepository expenses;
    AuthService auth;
    ReceiptServiceImpl service;

    @BeforeEach void prepare() {
        root=temp.resolve("receipts");receipts=new MockReceiptRepository();
        expenses=mock(ExpenseRepository.class);auth=mock(AuthService.class);
        when(auth.isOrganizationMember(1L,1L)).thenReturn(true);
        service=new ReceiptServiceImpl(receipts,expenses,auth,root.toString(),10485760,50000000);
    }
    byte[] smallImage() throws IOException {
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",output);
        return output.toByteArray();
    }
    Receipt receipt(String path) {
        return receipts.save(new Receipt("test",1L,1L,path,"test.png","image/png",10L,null,Instant.now(),true));
    }
    void status(HttpStatus expected,org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected,assertThrows(ResponseStatusException.class,action).getStatusCode());
    }
    long files() throws IOException {
        if(!Files.exists(root))return 0;
        try(var paths=Files.walk(root)){return paths.filter(Files::isRegularFile).count();}
    }
    static void chunk(DataOutputStream out,String type,byte[] content) throws IOException {
        byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var crc=new CRC32();crc.update(name);crc.update(content);
        out.writeInt(content.length);out.write(name);out.write(content);out.writeInt((int)crc.getValue());
    }
    static byte[] largeDimensionsPng() throws IOException {
        var encoded=new ByteArrayOutputStream();
        try(var deflater=new DeflaterOutputStream(encoded)) {
            byte[] scanline=new byte[10001];
            for(int i=0;i<10000;i++)deflater.write(scanline);
        }
        var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        var header=new ByteArrayOutputStream();var ihdr=new DataOutputStream(header);
        ihdr.writeInt(10000);ihdr.writeInt(10000);ihdr.write(new byte[]{8,0,0,0,0});
        chunk(out,"IHDR",header.toByteArray());chunk(out,"IDAT",encoded.toByteArray());chunk(out,"IEND",new byte[0]);
        return buffer.toByteArray();
    }

    @Test void rejectsHundredMegapixelsBeforeAllocatingDecodedImage() throws Exception {
        byte[] image=largeDimensionsPng();assertTrue(image.length<1048576);
        status(HttpStatus.BAD_REQUEST,()->service.storeReceipt(1L,1L,new MockMultipartFile("file","large.png","image/png",image)));
        assertEquals(0,files());assertTrue(receipts.findByOrganizationId(1L).isEmpty());
    }
    @Test void rejectsFinalSymlinkOutsideStorage() throws Exception {
        Files.createDirectories(root.resolve("org_1"));
        var outside=Files.writeString(temp.resolve("outside.txt"),"synthetic");
        Files.createSymbolicLink(root.resolve("org_1/link.png"),outside);
        receipt("org_1/link.png");
        status(HttpStatus.NOT_FOUND,()->service.loadReceiptFile(1L,"test",1L));
    }
    @Test void rechecksSymlinkWhenStreamingStarts() throws Exception {
        Files.createDirectories(root.resolve("org_1"));
        var file=Files.write(root.resolve("org_1/file.png"),smallImage());receipt("org_1/file.png");
        var resource=service.loadReceiptFile(1L,"test",1L);
        Files.delete(file);Files.createSymbolicLink(file,Files.writeString(temp.resolve("outside.txt"),"synthetic"));
        status(HttpStatus.NOT_FOUND,()->resource.getInputStream());
    }
    @Test void rejectsSymlinkDirectoryBeforeWriting() throws Exception {
        Files.createDirectories(root);var outside=Files.createDirectory(temp.resolve("outside"));
        Files.createSymbolicLink(root.resolve("org_1"),outside);
        status(HttpStatus.NOT_FOUND,()->service.storeReceipt(1L,1L,new MockMultipartFile("file","safe.png","image/png",smallImage())));
        try(var children=Files.list(outside)){assertEquals(0,children.count());}
    }
    @Test void refusesAStoredPathOfAnotherOrganization() throws Exception {
        Files.createDirectories(root.resolve("org_2"));Files.write(root.resolve("org_2/file.png"),smallImage());
        receipt("org_2/file.png");status(HttpStatus.NOT_FOUND,()->service.loadReceiptFile(1L,"test",1L));
    }
    @Test void removesOnlyTheNewFileOnDatabaseFailure() throws Exception {
        Files.createDirectories(root.resolve("org_1"));var existing=Files.writeString(root.resolve("org_1/existing.png"),"existing");
        var failing=mock(ReceiptRepository.class);
        when(failing.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic"));
        var service=new ReceiptServiceImpl(failing,expenses,auth,root.toString(),10485760,50000000);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                ()->service.storeReceipt(1L,1L,new MockMultipartFile("file","safe.png","image/png",smallImage())));
        assertEquals(1,files());assertEquals("existing",Files.readString(existing));
    }
    @Test void validatesFilenameBeforeWriting() throws Exception {
        for(String name:List.of("a".repeat(256)+".png","bad\r\nname.png"))
            status(HttpStatus.BAD_REQUEST,()->service.storeReceipt(1L,1L,new MockMultipartFile("file",name,"image/png",smallImage())));
        assertEquals(0,files());
    }
    @Test void refusesForeignExpenseAndInactiveMembership() {
        receipt("org_1/file.png");var foreign=new Expense();foreign.setOrganizationId(2L);foreign.setIsActive(true);
        when(expenses.findById(2L)).thenReturn(Optional.of(foreign));
        status(HttpStatus.NOT_FOUND,()->service.linkReceiptToExpense("test",2L,1L,1L));
        when(auth.isOrganizationMember(1L,1L)).thenReturn(false);
        status(HttpStatus.FORBIDDEN,()->service.linkReceiptToExpense("test",2L,1L,1L));
        assertNull(receipts.findById("test").orElseThrow().getExpenseId());
    }
}

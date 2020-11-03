declare 
    cursor c1( 
      p_rows_to_process in number) is 
      select * 
      from   siga.ex_preenchimento 
      where  id_arq is null 
         and preenchimento_blob is not null; 
    l_moretodo       boolean := true; 
    l_rows_processed pls_integer := 0; 
    l_chunk          number := 1000; 
begin 
    while l_moretodo loop 
        l_rows_processed := 0; 

        for r1 in c1(l_chunk) loop 
            insert into corporativo.cp_arquivo_blob
                        (id_arq_blob,conteudo_arq_blob) 
            values      (corporativo.cp_arquivo_seq.nextval,r1.preenchimento_blob);

            insert into corporativo.cp_arquivo
                        (id_arq,id_orgao_usu,conteudo_tp_arq) 
            values      (corporativo.cp_arquivo_seq.currval,null,'application/x-www-form-urlencoded');

            update siga.ex_preenchimento
            set    id_arq = corporativo.cp_arquivo_seq.currval 
            where  id_preenchimento = r1.id_preenchimento; 

            l_rows_processed := l_rows_processed + 1; 
        end loop; 

        commit; 

        dbms_output.Put_line('Processed: ' 
                             ||l_rows_processed); 

        if l_rows_processed < l_chunk then 
          dbms_output.Put_line('Exiting loop...'); 

          l_moretodo := false; 
        end if; 
    end loop; 
end;
